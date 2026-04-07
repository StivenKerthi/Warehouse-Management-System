package com.example.wms.delivery.scheduler;

import com.example.wms.delivery.model.Delivery;
import com.example.wms.delivery.repository.DeliveryRepository;
import com.example.wms.messaging.dto.FulfillmentEvent;
import com.example.wms.messaging.producer.FulfillmentEventProducer;
import com.example.wms.order.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Nightly cron job that transitions all due {@code UNDER_DELIVERY} orders to
 * {@code FULFILLED} and publishes a Kafka fulfillment event for each one.
 *
 * <h2>Schedule</h2>
 * {@code "0 1 0 * * MON-FRI"} — fires at 00:01:00 on every weekday.
 * The 1-minute offset from midnight ensures any weekend backlog of deliveries
 * dated on the preceding Friday is included (delivery date {@code <= today}).
 *
 * <h2>Per-order isolation</h2>
 * Each order is processed in its own {@code REQUIRES_NEW} transaction via
 * {@link FulfillmentProcessor#fulfill}. A failure on order N rolls back only
 * that order's transaction; the loop continues with order N+1. The failed order
 * retains its {@code UNDER_DELIVERY} status and will be retried on the next
 * cron run.
 *
 * <h2>Kafka publish timing</h2>
 * {@link FulfillmentEventProducer#publish} is called <em>after</em>
 * {@link FulfillmentProcessor#fulfill} returns, meaning after the per-order
 * transaction has committed. This guarantees the Kafka consumer observes the
 * {@code FULFILLED} state in the database before processing the event.
 *
 * <h2>MDC context</h2>
 * A fresh correlation ID is generated per cron run and placed in MDC so every
 * log line within the run can be grouped in a log aggregator. The MDC entries
 * are cleared in a {@code finally} block to prevent leakage across scheduler
 * thread reuse.
 *
 * <h2>Multi-instance safety (production note)</h2>
 * In a horizontally scaled deployment, this job will fire on every instance
 * simultaneously. Add <a href="https://github.com/lukas-krecan/ShedLock">ShedLock</a>
 * to ensure only one instance acquires the lock and runs the job. The per-order
 * idempotency enforced by the {@code fulfilment_log.order_id} PK guards against
 * duplicate Kafka events even without ShedLock, but duplicate DB transitions
 * must be prevented at the scheduler level.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentScheduler {

    private static final String MDC_CORRELATION_KEY = "correlationId";
    private static final String MDC_USERNAME_KEY    = "username";

    private final DeliveryRepository deliveryRepository;
    private final FulfillmentProcessor processor;
    private final FulfillmentEventProducer producer;

    // ── Cron entry point ─────────────────────────────────────────────────────

    /**
     * Cron trigger: 00:01 every weekday (MON–FRI).
     *
     * <p>This method is intentionally <strong>not</strong> {@code @Transactional}.
     * Per-order transactions are handled by {@link FulfillmentProcessor#fulfill},
     * which is on a separate bean so Spring AOP can intercept the call correctly.
     */
    //@Scheduled(cron = "0 1 0 * * MON-FRI")
    @Scheduled(cron = "0 */5 * * * *")
    public void runFulfillment() {
        String runId = UUID.randomUUID().toString();
        MDC.put(MDC_CORRELATION_KEY, runId);
        MDC.put(MDC_USERNAME_KEY, FulfillmentProcessor.SYSTEM_USER);
        try {
            doRun(runId);
        } finally {
            MDC.remove(MDC_CORRELATION_KEY);
            MDC.remove(MDC_USERNAME_KEY);
        }
    }

    // ── Core logic ───────────────────────────────────────────────────────────

    /**
     * Loads all due deliveries, processes each order in isolation, and
     * publishes a Kafka event per fulfilled order.
     *
     * <p>This is a separate method (not inlined in {@link #runFulfillment})
     * so the MDC setup/teardown in the caller stays clean regardless of
     * how the processing path exits.
     *
     * @param runId the correlation ID for this cron execution (for log correlation)
     */
    private void doRun(String runId) {
        LocalDate today = LocalDate.now();

        log.info("Fulfillment cron started: date={}, runId={}", today, runId);

        // Load all UNDER_DELIVERY orders whose delivery date has arrived.
        // DeliveryRepository.findByDeliveryDateLessThanEqualAndOrderStatus uses
        // JOIN FETCH so delivery.getOrder().getId() and delivery.getDeliveryDate()
        // are immediately accessible without lazy-load issues.
        List<Delivery> due = deliveryRepository
                .findByDeliveryDateLessThanEqualAndOrderStatus(today, OrderStatus.UNDER_DELIVERY);

        if (due.isEmpty()) {
            log.info("Fulfillment cron: no orders due today, exiting");
            return;
        }

        log.info("Fulfillment cron: {} order(s) to process", due.size());

        int succeeded = 0;
        int failed    = 0;

        for (Delivery delivery : due) {
            UUID orderId = delivery.getOrder().getId();
            LocalDate deliveryDate = delivery.getDeliveryDate();

            try {
                // ── Step 1: commit DB transition in a REQUIRES_NEW transaction ─────
                // FulfillmentProcessor.fulfill() opens and commits its own transaction.
                // Any exception here rolls back only this order.
                FulfillmentEvent event = processor.fulfill(orderId, deliveryDate);

                // ── Step 2: publish Kafka event after the transaction has committed ─
                // Calling publish() outside the transaction ensures the consumer sees
                // the FULFILLED state in the DB before processing the event.
                // If publish() fails, the order stays FULFILLED in the DB and the
                // missed event is a known acceptable gap (consumer idempotency in
                // fulfilment_log guards against duplicates on retry paths).
                producer.publish(event);

                succeeded++;
                log.info("Fulfillment cron: order fulfilled: orderId={}, deliveryDate={}",
                        orderId, deliveryDate);

            } catch (Exception ex) {
                failed++;
                log.error("Fulfillment cron: failed to fulfill order: orderId={}, deliveryDate={} — " +
                          "order retains UNDER_DELIVERY status and will retry on next run",
                        orderId, deliveryDate, ex);
                // Continue — one failure must not block remaining orders
            }
        }

        log.info("Fulfillment cron completed: succeeded={}, failed={}, total={}",
                succeeded, failed, due.size());
    }
}

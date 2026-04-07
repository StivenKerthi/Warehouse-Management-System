package com.example.wms.messaging.consumer;

import com.example.wms.messaging.dto.FulfillmentEvent;
import com.example.wms.messaging.model.FulfilmentLog;
import com.example.wms.messaging.producer.FulfillmentEventProducer;
import com.example.wms.messaging.repository.FulfilmentLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka consumer for the {@code warehouse.order.fulfilled} topic.
 *
 * <h2>Delivery semantics</h2>
 * The container factory ({@code kafkaListenerContainerFactory}) is configured
 * with {@code AckMode.MANUAL} — this listener <strong>must</strong> call
 * {@link Acknowledgment#acknowledge()} explicitly, or the offset will not be
 * committed and the message will be re-delivered.
 *
 * <h2>Retry &amp; DLQ</h2>
 * The container factory is also wired with a {@code DefaultErrorHandler} backed
 * by {@code FixedBackOff(0ms, maxRetryAttempts-1)} and a
 * {@code DeadLetterPublishingRecoverer}. When this method throws <em>any</em>
 * uncaught exception:
 * <ol>
 *   <li>The error handler retries the listener in-place (same thread, same
 *       consumer record) up to {@code max-retry-attempts - 1} times.</li>
 *   <li>After exhausting retries, the record is published to
 *       {@code warehouse.order.fulfilled.dlq} and the offset is committed,
 *       unblocking the partition.</li>
 * </ol>
 * The listener must therefore <strong>never swallow exceptions on the failure
 * path</strong> — only the idempotency duplicate case is caught and ACK'd.
 *
 * <h2>Idempotency</h2>
 * Before inserting into {@code fulfilment_log}, the consumer checks
 * {@link FulfilmentLogRepository#existsById} (runs in a dedicated read-only
 * transaction). If the record already exists, the event is a duplicate — it is
 * logged at WARN and ACK'd without re-inserting.
 *
 * <p>The {@code fulfilment_log.order_id} primary key is the ultimate
 * idempotency guard: even in the theoretical race between {@code existsById}
 * and {@code saveAndFlush}, a concurrent duplicate insert would throw a
 * {@code DataIntegrityViolationException}, which is not caught here and
 * propagates to the error handler. On the next retry, {@code existsById}
 * finds the record and ACKs.
 *
 * <h2>MDC propagation</h2>
 * The producer embeds {@code correlationId} and {@code username} as Kafka
 * record headers. This listener extracts those headers and places them in
 * {@link MDC} before processing, so every log line within this invocation
 * carries the same correlation ID as the original HTTP request that triggered
 * the fulfillment. MDC entries are always cleared in a {@code finally} block
 * even when an exception propagates to the error handler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentEventConsumer {

    private static final String MDC_CORRELATION_KEY = "correlationId";
    private static final String MDC_USERNAME_KEY    = "username";

    private final FulfilmentLogRepository fulfilmentLogRepository;

    // ── Listener ─────────────────────────────────────────────────────────────

    /**
     * Processes a single {@link FulfillmentEvent} from the fulfillment topic.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Extract {@code correlationId} and {@code username} from Kafka headers
     *       into MDC (enables correlated logging across producer → consumer).</li>
     *   <li>Check {@code fulfilment_log} for a duplicate via {@code existsById} —
     *       if found, log WARN and ACK without re-processing.</li>
     *   <li>Insert a new {@link FulfilmentLog} row via {@code saveAndFlush()}.
     *       Any exception here is <em>not</em> caught — it propagates to the
     *       {@code DefaultErrorHandler} for retry and eventual DLQ routing.</li>
     *   <li>ACK the offset only after a successful insert.</li>
     *   <li>Clear MDC in {@code finally} regardless of outcome.</li>
     * </ol>
     *
     * @param event  the deserialised fulfillment event payload
     * @param ack    manual acknowledgment handle — must be called on success or
     *               duplicate; must NOT be called on failure
     * @param record the raw Kafka record, used to read MDC headers
     */
    @KafkaListener(
            topics         = "${app.kafka.topics.fulfillment}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload FulfillmentEvent event,
            Acknowledgment ack,
            ConsumerRecord<String, FulfillmentEvent> record
    ) {
        restoreMdc(record.headers());

        try {
            log.info("Fulfillment event received: orderId={}, client={}, items={}, deliveryDate={}",
                    event.getOrderId(),
                    event.getClientUsername(),
                    event.getItems() != null ? event.getItems().size() : 0,
                    event.getDeliveryDate());

            // ── Idempotency check ─────────────────────────────────────────────
            // existsById runs in its own read-only transaction (Spring Data default).
            // A true result means this event was already processed successfully —
            // ACK and skip without a second insert.
            if (fulfilmentLogRepository.existsById(event.getOrderId())) {
                log.warn("Duplicate fulfillment event — already logged, ACKing without re-insert: " +
                         "orderId={}, partition={}, offset={}",
                        event.getOrderId(), record.partition(), record.offset());
                ack.acknowledge();
                return;
            }

            // ── Persist fulfilment log ────────────────────────────────────────
            // saveAndFlush() issues the INSERT immediately (not at tx commit time),
            // so any constraint violation surfaces here, not later.
            // No try-catch: any exception must propagate to DefaultErrorHandler
            // so that retry counting and DLQ routing work correctly.
            FulfilmentLog entry = FulfilmentLog.of(event);
            fulfilmentLogRepository.saveAndFlush(entry);

            // ── Acknowledge only after successful insert ───────────────────────
            ack.acknowledge();

            log.info("Fulfillment event processed: orderId={}, client={}, itemCount={}, fulfilledAt={}",
                    entry.getOrderId(),
                    entry.getClient(),
                    entry.getItemCount(),
                    entry.getFulfilledAt());

        } finally {
            // Always clear MDC — consumer threads are pooled and reused.
            // Leftover values from this invocation must not bleed into the next.
            MDC.remove(MDC_CORRELATION_KEY);
            MDC.remove(MDC_USERNAME_KEY);
        }
    }

    // ── MDC helpers ───────────────────────────────────────────────────────────

    /**
     * Reads {@code correlationId} and {@code username} from Kafka record headers
     * and sets them into MDC.
     *
     * <p>The producer always writes these headers (defaulting to empty string when
     * MDC is absent on the cron thread). A missing or empty header results in an
     * empty-string MDC value — never a null — which is safe for log pattern
     * substitution.
     *
     * @param headers the Kafka record headers from the inbound {@link ConsumerRecord}
     */
    private void restoreMdc(Headers headers) {
        MDC.put(MDC_CORRELATION_KEY, readHeader(headers, FulfillmentEventProducer.HEADER_CORRELATION_ID));
        MDC.put(MDC_USERNAME_KEY,    readHeader(headers, FulfillmentEventProducer.HEADER_USERNAME));
    }

    /**
     * Decodes a Kafka header value as a UTF-8 string.
     *
     * @param headers the record headers
     * @param name    the header name to look up
     * @return the decoded header value, or an empty string if the header is absent
     */
    private String readHeader(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return "";
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}

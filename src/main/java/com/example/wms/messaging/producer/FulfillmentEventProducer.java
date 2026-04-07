package com.example.wms.messaging.producer;

import com.example.wms.messaging.dto.FulfillmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Publishes {@link FulfillmentEvent} instances to the fulfillment Kafka topic.
 *
 * <p>Design notes:
 * <ul>
 *   <li><strong>Message key = orderId string.</strong>  Ensures all events for the
 *       same order land on the same partition (strict ordering per order).</li>
 *   <li><strong>MDC headers.</strong>  {@code correlationId} and {@code username}
 *       are read from {@link MDC} and embedded as Kafka record headers so the
 *       consumer can restore them into its own MDC before processing — enabling
 *       end-to-end correlation across thread boundaries.</li>
 *   <li><strong>Async send.</strong>  {@code KafkaTemplate.send()} is non-blocking.
 *       The result callback logs success (with partition + offset) or failure.
 *       The caller (the cron scheduler) is never blocked waiting for a broker ACK.</li>
 *   <li><strong>Null-safe MDC reads.</strong>  The cron scheduler runs on a
 *       {@code @Scheduled} thread where HTTP-layer MDC entries are absent.
 *       Missing values default to an empty string so headers are always present
 *       (the consumer side must handle empty strings gracefully).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentEventProducer {

    /** Kafka header name — read by the consumer to restore MDC correlation ID. */
    public static final String HEADER_CORRELATION_ID = "correlationId";

    /** Kafka header name — read by the consumer to restore MDC username. */
    public static final String HEADER_USERNAME = "username";

    @Value("${app.kafka.topics.fulfillment}")
    private String topic;

    private final KafkaTemplate<String, FulfillmentEvent> kafkaTemplate;

    /**
     * Publishes the event asynchronously.
     *
     * <p>Intended to be called <em>after</em> the database transaction has committed
     * (i.e., from the cron job after {@code OrderStateMachine.transition(FULFILLED)}).
     * Publishing before commit risks the consumer processing an event whose DB state
     * is not yet visible.
     *
     * @param event the fully populated fulfillment event — must not be {@code null}
     */
    public void publish(FulfillmentEvent event) {
        String correlationId = mdcOrEmpty(HEADER_CORRELATION_ID);
        String username      = mdcOrEmpty(HEADER_USERNAME);

        ProducerRecord<String, FulfillmentEvent> record = new ProducerRecord<>(
                topic,
                null,                            // partition — Kafka assigns via key hash
                event.getOrderId().toString(),   // key — partition affinity per order
                event
        );

        // Embed MDC context as Kafka headers for consumer-side MDC propagation
        record.headers()
              .add(new RecordHeader(HEADER_CORRELATION_ID, toBytes(correlationId)))
              .add(new RecordHeader(HEADER_USERNAME,        toBytes(username)));

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish fulfillment event: orderId={}, correlationId={}",
                                event.getOrderId(), correlationId, ex);
                    } else {
                        var meta = result.getRecordMetadata();
                        log.info("Published fulfillment event: orderId={}, topic={}, partition={}, offset={}, correlationId={}",
                                event.getOrderId(),
                                meta.topic(),
                                meta.partition(),
                                meta.offset(),
                                correlationId);
                    }
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the MDC value for {@code key}, or an empty string if absent.
     * Never returns {@code null} — Kafka header bytes must always be defined.
     */
    private String mdcOrEmpty(String key) {
        String value = MDC.get(key);
        return value != null ? value : "";
    }

    private byte[] toBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

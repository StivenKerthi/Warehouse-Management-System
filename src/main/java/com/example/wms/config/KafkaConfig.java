package com.example.wms.config;

import com.example.wms.messaging.dto.FulfillmentEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka infrastructure configuration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Declare {@link NewTopic} beans so Admin automatically creates topics
 *       on startup (idempotent — safe if topics already exist).</li>
 *   <li>Build an explicit {@link ProducerFactory} / {@link KafkaTemplate} with
 *       {@code JsonSerializer} for {@link FulfillmentEvent} payloads.</li>
 *   <li>Build an explicit {@link ConsumerFactory} with {@code JsonDeserializer}
 *       restricted to the {@code com.example.wms.messaging.dto} package
 *       (trusted-packages guard).</li>
 *   <li>Configure a {@link ConcurrentKafkaListenerContainerFactory} with
 *       {@link ContainerProperties.AckMode#MANUAL} and a
 *       {@link DefaultErrorHandler} that routes to the DLQ after
 *       {@code app.kafka.consumer.max-retry-attempts} failures.</li>
 * </ul>
 *
 * <p>Property sources (application.yml):
 * <ul>
 *   <li>{@code spring.kafka.bootstrap-servers}</li>
 *   <li>{@code spring.kafka.consumer.group-id}</li>
 *   <li>{@code app.kafka.topics.fulfillment}</li>
 *   <li>{@code app.kafka.topics.fulfillment-dlq}</li>
 *   <li>{@code app.kafka.consumer.max-retry-attempts}</li>
 * </ul>
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    // ── Injected properties ──────────────────────────────────────────────────

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${app.kafka.topics.fulfillment}")
    private String fulfillmentTopic;

    @Value("${app.kafka.topics.fulfillment-dlq}")
    private String fulfillmentDlqTopic;

    @Value("${app.kafka.consumer.max-retry-attempts}")
    private int maxRetryAttempts;

    // ── Topic declarations ───────────────────────────────────────────────────

    /**
     * Main fulfillment topic: 1 partition, replication factor 1
     * (single-broker dev/test setup; scale up via config in production).
     */
    @Bean
    public NewTopic fulfillmentTopic() {
        return TopicBuilder.name(fulfillmentTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter queue: receives events that fail after all retry attempts.
     * Replication factor matches main topic — both are single-broker here.
     */
    @Bean
    public NewTopic fulfillmentDlqTopic() {
        return TopicBuilder.name(fulfillmentDlqTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ── Producer ─────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, FulfillmentEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Strongest delivery guarantee: wait for all in-sync replica ACKs
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Max in-flight must be <= 5 when idempotence is enabled
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        // Do not embed type headers — the consumer side controls type mapping
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, FulfillmentEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ─────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, FulfillmentEvent> consumerFactory() {
        JsonDeserializer<FulfillmentEvent> deserializer =
                new JsonDeserializer<>(FulfillmentEvent.class, false);
        // Restrict deserialisation to our own DTO package (security hardening)
        deserializer.addTrustedPackages("com.example.wms.messaging.dto");
        // Ignore type headers — the target type is fixed by the factory above
        deserializer.setUseTypeHeaders(false);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // manual ACK
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30_000);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * Container factory used by {@code @KafkaListener} methods.
     *
     * <p>Key settings:
     * <ul>
     *   <li>{@link ContainerProperties.AckMode#MANUAL} — listeners call
     *       {@code Acknowledgment.acknowledge()} explicitly after successful
     *       processing. Failed messages are NOT acknowledged and will be
     *       re-delivered up to {@code maxRetryAttempts} times.</li>
     *   <li>{@link DefaultErrorHandler} with {@link FixedBackOff}(0ms, maxRetries)
     *       then {@link DeadLetterPublishingRecoverer} — after exhausting retries
     *       the event is published to the DLQ and the offset is committed so the
     *       main topic is not blocked.</li>
     * </ul>
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FulfillmentEvent>
    kafkaListenerContainerFactory() {

        DeadLetterPublishingRecoverer dlqRecoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate(),
                        (record, ex) -> new org.apache.kafka.common.TopicPartition(
                                fulfillmentDlqTopic, 0));

        // No delay between retries — idempotency in the consumer makes instant
        // retry safe. maxRetryAttempts - 1 because the first attempt is not a retry.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                dlqRecoverer,
                new FixedBackOff(0L, maxRetryAttempts - 1L));

        ConcurrentKafkaListenerContainerFactory<String, FulfillmentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}

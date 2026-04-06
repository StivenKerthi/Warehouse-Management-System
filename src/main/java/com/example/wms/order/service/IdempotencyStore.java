package com.example.wms.order.service;

import com.example.wms.order.dto.IdempotentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes idempotency records to Redis for order submission.
 *
 * <h2>Key schema</h2>
 * <pre>idempotency:{userId}:{idempotencyKey}</pre>
 * TTL: configurable via {@code app.idempotency.ttl-hours} (default 24 h).
 *
 * <h2>Serialisation</h2>
 * Uses {@code StringRedisTemplate} — Redis stores plain UTF-8 JSON. The
 * injected {@link ObjectMapper} is Spring Boot's auto-configured instance
 * (not the {@code RedisConfig} one, which has polymorphic type info enabled
 * for the cache manager). {@link IdempotentResponse} and its nested
 * {@link com.example.wms.order.dto.OrderDto} are simple records that Jackson
 * 2.14+ (bundled with Spring Boot 3) handles natively.
 *
 * <h2>Failure handling</h2>
 * A Redis failure on {@link #store} is logged as WARN and swallowed —
 * the caller's DB transaction has already committed successfully.
 * A Redis failure on {@link #find} is logged as WARN and returns
 * {@link Optional#empty()}, allowing the request to re-execute (safe
 * because the business operation itself is idempotent for status transitions).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Spring Boot's auto-configured Jackson {@code ObjectMapper}.
     * Configured via {@code spring.jackson.*} in application.yml:
     * ISO-8601 dates, non_null inclusion, JavaTimeModule registered.
     */
    private final ObjectMapper objectMapper;

    @Value("${app.idempotency.ttl-hours:24}")
    private long ttlHours;

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Looks up a previously stored idempotency record.
     *
     * @param userId         the authenticated client's UUID (scope isolation)
     * @param idempotencyKey the client-provided idempotency key
     * @return the stored response, or {@link Optional#empty()} on cache miss or
     *         deserialisation failure
     */
    public Optional<IdempotentResponse> find(UUID userId, String idempotencyKey) {
        String key = buildKey(userId, idempotencyKey);
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for idempotency key '{}'. Treating as cache miss.", key, e);
            return Optional.empty();
        }

        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, IdempotentResponse.class));
        } catch (JsonProcessingException e) {
            // Corrupt entry — treat as miss so the request can re-execute
            log.warn("Failed to deserialise cached idempotency response for key '{}'. " +
                     "Treating as cache miss.", key, e);
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Stores an idempotency record so duplicate submissions can be replayed.
     *
     * <p>Failures are logged as WARN and swallowed — the DB transaction has already
     * committed at this point. The worst case of a missing record is a duplicate
     * execution, which is acceptable given the operation is idempotent for the
     * underlying status transition.
     *
     * @param userId          the authenticated client's UUID
     * @param idempotencyKey  the client-provided idempotency key
     * @param response        the response to cache
     */
    public void store(UUID userId, String idempotencyKey, IdempotentResponse response) {
        String key = buildKey(userId, idempotencyKey);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(ttlHours));
            log.debug("Stored idempotency record for key '{}' (TTL {}h)", key, ttlHours);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise idempotency response for key '{}'. " +
                     "Idempotency protection is degraded for this request.", key, e);
        } catch (Exception e) {
            log.warn("Redis write failed for idempotency key '{}'. " +
                     "Idempotency protection is degraded for this request.", key, e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildKey(UUID userId, String idempotencyKey) {
        return KEY_PREFIX + userId + ":" + idempotencyKey;
    }
}

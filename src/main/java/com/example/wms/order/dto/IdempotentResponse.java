package com.example.wms.order.dto;

/**
 * Envelope stored in Redis to support idempotent order submission.
 *
 * <p>Keyed as {@code idempotency:{userId}:{idempotencyKey}} with a 24-hour TTL.
 * On a duplicate submission the controller returns exactly the original HTTP status
 * code and body without re-executing the business logic.
 *
 * <p>Stored as a JSON string via {@code StringRedisTemplate} + Jackson.
 * Jackson 2.14+ (bundled with Spring Boot 3) natively handles Java records,
 * so no custom deserializer is needed.
 *
 * <p>{@code httpStatus} is preserved so a future endpoint that returns 201 on
 * first call can still replay the correct status on a duplicate.
 */
public record IdempotentResponse(
        int httpStatus,
        OrderDto body
) {}

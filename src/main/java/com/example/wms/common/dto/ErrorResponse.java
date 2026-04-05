package com.example.wms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Uniform error envelope returned for every non-2xx response.
 *
 * <pre>
 * {
 *   "timestamp":     "2025-06-01T10:23:45.123Z",
 *   "status":        422,
 *   "code":          "INSUFFICIENT_TRUCK_VOLUME",
 *   "message":       "...",
 *   "correlationId": "a1b2c3d4-..."
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int     status,
        String  code,
        String  message,
        String  correlationId
) {

    /** Convenience factory — timestamp is always set to now. */
    public static ErrorResponse of(int status, String code, String message, String correlationId) {
        return new ErrorResponse(Instant.now(), status, code, message, correlationId);
    }
}

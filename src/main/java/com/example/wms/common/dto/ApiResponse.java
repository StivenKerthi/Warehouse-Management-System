package com.example.wms.common.dto;

/**
 * Uniform envelope for single-resource responses.
 *
 * <p>Used on endpoints that return one item (create, get-by-id, update).
 * Keeps the JSON contract consistent with {@link PagedResponse}: callers
 * always unwrap a {@code data} field rather than receiving a bare object.
 *
 * <pre>
 * {
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>Intentionally minimal — no timestamp or status field here because
 * those concerns live in {@link ErrorResponse} on the error path only.
 * Success responses carry just the resource.
 *
 * @param <T> the resource type
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}

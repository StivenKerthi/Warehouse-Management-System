package com.example.wms.order.dto;

import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Slim read-only projection of an order for paginated list views and CSV export.
 *
 * <p>Does not include order items — use {@link OrderDto} for the full detail view.
 * Keeping items out of the list projection avoids N+1 queries on paginated results.
 *
 * <p>{@code submittedAt} is null for orders that have not yet been submitted
 * (still in {@code CREATED} status).
 */
public record OrderSummaryDto(
        UUID id,
        String orderNumber,
        String clientUsername,
        OrderStatus status,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt
) {
    /**
     * Maps a managed {@link Order} entity to this DTO.
     *
     * <p>Accesses {@code order.getClient().getUsername()} — must be called within
     * an active transaction to avoid {@code LazyInitializationException} on the
     * lazy {@code client} association.
     */
    public static OrderSummaryDto from(Order order) {
        return new OrderSummaryDto(
                order.getId(),
                order.getOrderNumber(),
                order.getClient().getUsername(),
                order.getStatus(),
                order.getSubmittedAt(),
                order.getCreatedAt()
        );
    }
}

package com.example.wms.order.dto;

import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full detail view of a single order, including all line items.
 *
 * <p>Returned by create, get-by-id, and update endpoints. For paginated list views
 * (where loading all items per row is wasteful), use {@link OrderSummaryDto} instead.
 *
 * <p>{@code declineReason} is non-null only when {@code status == DECLINED}.
 * {@code submittedAt} is non-null only after the order has been submitted at least once.
 */
public record OrderDto(
        UUID id,
        String orderNumber,
        String clientUsername,
        OrderStatus status,
        String declineReason,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderItemDto> items
) {
    /**
     * Maps a managed {@link Order} entity to this DTO.
     *
     * <p>Accesses {@code order.getOrderItems()} and each item's {@code inventoryItem} —
     * must be called within an active transaction to avoid {@code LazyInitializationException}.
     */
    public static OrderDto from(Order order) {
        List<OrderItemDto> items = order.getOrderItems().stream()
                .map(OrderItemDto::from)
                .toList();

        return new OrderDto(
                order.getId(),
                order.getOrderNumber(),
                order.getClient().getUsername(),
                order.getStatus(),
                order.getDeclineReason(),
                order.getSubmittedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }
}

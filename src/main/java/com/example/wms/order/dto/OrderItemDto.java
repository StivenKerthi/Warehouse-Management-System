package com.example.wms.order.dto;

import com.example.wms.order.model.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only projection of a single {@code order_items} row.
 *
 * <p>{@code unitPriceSnapshot} reflects the price at the time the order was created —
 * it never changes even if the inventory item's price is updated later.
 *
 * <p>{@code deadlineDate} is nullable — only present when the client set a desired
 * delivery deadline. Exceeding it is a soft warning, not a hard block.
 */
public record OrderItemDto(
        UUID id,
        UUID inventoryItemId,
        String inventoryItemName,
        int requestedQuantity,
        BigDecimal unitPriceSnapshot,
        LocalDate deadlineDate
) {
    /**
     * Maps a managed {@link OrderItem} entity to this DTO.
     *
     * <p>Accesses {@code item.getInventoryItem()} — must be called within an active
     * transaction to avoid {@code LazyInitializationException}.
     */
    public static OrderItemDto from(OrderItem item) {
        return new OrderItemDto(
                item.getId(),
                item.getInventoryItem().getId(),
                item.getInventoryItem().getName(),
                item.getRequestedQuantity(),
                item.getUnitPriceSnapshot(),
                item.getDeadlineDate()
        );
    }
}

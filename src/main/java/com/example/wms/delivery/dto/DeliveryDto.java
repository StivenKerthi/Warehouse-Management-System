package com.example.wms.delivery.dto;

import com.example.wms.delivery.model.Delivery;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a scheduled delivery.
 *
 * <p>Returned by {@code POST /api/manager/orders/{id}/schedule} and used
 * by future GET endpoints that expose delivery details.
 *
 * @param id                     delivery UUID
 * @param orderId                the scheduled order's UUID
 * @param orderNumber            human-readable order reference (e.g. ORD-2025-00042)
 * @param deliveryDate           the target delivery date
 * @param scheduledAt            when the delivery was scheduled
 * @param scheduledBy            username of the manager who scheduled it
 * @param truckIds               UUIDs of all trucks assigned to this delivery
 * @param hasDeadlineWarning     {@code true} when any order item's {@code deadlineDate}
 *                               is before {@code deliveryDate} — informational only,
 *                               scheduling was NOT blocked
 * @param itemsExceedingDeadline names of inventory items whose deadline is exceeded;
 *                               empty when {@code hasDeadlineWarning} is false
 */
public record DeliveryDto(
        UUID id,
        UUID orderId,
        String orderNumber,
        LocalDate deliveryDate,
        OffsetDateTime scheduledAt,
        String scheduledBy,
        List<UUID> truckIds,
        boolean hasDeadlineWarning,
        List<String> itemsExceedingDeadline
) {
    /**
     * Builds a {@code DeliveryDto} from a managed {@link Delivery} entity.
     *
     * @param delivery               the fully-populated delivery (with truck assignments loaded)
     * @param itemsExceedingDeadline item names whose deadlines are exceeded; empty list if none
     */
    public static DeliveryDto from(Delivery delivery, List<String> itemsExceedingDeadline) {
        List<UUID> truckIds = delivery.getTruckAssignments().stream()
                .map(dt -> dt.getId().getTruckId())
                .toList();

        return new DeliveryDto(
                delivery.getId(),
                delivery.getOrder().getId(),
                delivery.getOrder().getOrderNumber(),
                delivery.getDeliveryDate(),
                delivery.getScheduledAt(),
                delivery.getScheduledBy(),
                truckIds,
                !itemsExceedingDeadline.isEmpty(),
                itemsExceedingDeadline
        );
    }
}

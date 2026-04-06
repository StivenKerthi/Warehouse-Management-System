package com.example.wms.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a single line item in a create-order or update-order request.
 *
 * <p>{@code deadlineDate} is optional — if provided it acts as a soft warning: the manager
 * is flagged when the scheduled delivery date exceeds it, but scheduling is never blocked.
 */
public record OrderItemRequest(

        @NotNull(message = "Inventory item ID must not be null")
        UUID inventoryItemId,

        @Min(value = 1, message = "Requested quantity must be at least 1")
        int requestedQuantity,

        /** Optional client-requested delivery deadline. Soft warning only — does not block scheduling. */
        LocalDate deadlineDate
) {}

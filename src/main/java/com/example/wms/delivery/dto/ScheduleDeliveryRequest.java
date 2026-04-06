package com.example.wms.delivery.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/manager/orders/{id}/schedule}.
 *
 * @param date     target delivery date; must be a weekday within the configured window
 * @param truckIds one or more truck UUIDs to assign; trucks must be active and
 *                 have combined volume >= the order's total required volume
 */
public record ScheduleDeliveryRequest(

        @NotNull(message = "Delivery date is required")
        LocalDate date,

        @NotNull(message = "truckIds list is required")
        @NotEmpty(message = "At least one truck must be specified")
        List<@NotNull(message = "Truck ID must not be null") UUID> truckIds
) {}

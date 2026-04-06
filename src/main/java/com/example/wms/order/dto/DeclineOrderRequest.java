package com.example.wms.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/manager/orders/{id}/decline}.
 *
 * <p>The reason is required and stored verbatim on the order so the client
 * can see why their submission was rejected.
 */
public record DeclineOrderRequest(

        @NotBlank(message = "Decline reason must not be blank")
        @Size(max = 1000, message = "Decline reason must not exceed 1000 characters")
        String reason
) {}

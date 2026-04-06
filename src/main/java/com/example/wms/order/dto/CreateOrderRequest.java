package com.example.wms.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code POST /api/orders}.
 *
 * <p>At least one item is required — an order with no line items is not meaningful and
 * is rejected by the {@code @NotEmpty} constraint before reaching the service layer.
 */
public record CreateOrderRequest(

        @NotEmpty(message = "An order must contain at least one item")
        @Valid
        List<OrderItemRequest> items
) {}

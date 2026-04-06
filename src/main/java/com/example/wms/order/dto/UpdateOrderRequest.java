package com.example.wms.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code PATCH /api/orders/{id}}.
 *
 * <p>Replaces the order's complete item list with the provided items. The service rejects
 * requests that would leave the order with no items ({@code @NotEmpty}).
 *
 * <p>Updates are only allowed when the order is in {@code CREATED} or {@code DECLINED} status.
 * The service enforces this rule and throws a {@code BusinessException} (HTTP 422) otherwise.
 */
public record UpdateOrderRequest(

        @NotEmpty(message = "An order must contain at least one item")
        @Valid
        List<OrderItemRequest> items
) {}

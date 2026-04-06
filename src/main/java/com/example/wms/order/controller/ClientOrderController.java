package com.example.wms.order.controller;

import com.example.wms.common.dto.ApiResponse;
import com.example.wms.common.dto.PagedResponse;
import com.example.wms.order.dto.AuditLogEntryDto;
import com.example.wms.order.dto.CreateOrderRequest;
import com.example.wms.order.dto.OrderDto;
import com.example.wms.order.dto.OrderSummaryDto;
import com.example.wms.order.dto.UpdateOrderRequest;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.service.OrderAuditService;
import com.example.wms.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing order endpoints — accessible to users with the {@code CLIENT} role only.
 *
 * <h2>Security</h2>
 * <p>URL-level role enforcement is applied in {@code SecurityConfig}:
 * {@code /api/orders/**} → {@code hasRole("CLIENT")}.
 * Every endpoint additionally enforces ownership — a CLIENT can only read or modify
 * their own orders. Non-owned and non-existent orders both return HTTP 404 to prevent
 * existence enumeration.
 *
 * <h2>CSV export</h2>
 * <p>The list endpoint ({@code GET /api/orders}) supports content negotiation.
 * Sending {@code Accept: text/csv} returns a downloadable CSV file via
 * {@link OrderCsvExporter}. The default ({@code Accept: application/json}) returns
 * a paginated {@link PagedResponse}.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Client — Orders", description = "Order management endpoints (CLIENT role only)")
@SecurityRequirement(name = "bearerAuth")
public class ClientOrderController {

    private final OrderService      orderService;
    private final OrderAuditService orderAuditService;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Creates a new order for the authenticated client.
     *
     * <p>The order starts in {@code CREATED} status. Item prices are snapshotted
     * at creation time — future changes to inventory prices do not affect this order.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create order",
            description = "Creates a new order in CREATED status. " +
                          "Unit prices are locked from current inventory prices at creation time.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "One or more inventory items not found")
    })
    public ApiResponse<OrderDto> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication auth) {

        UUID clientId = orderService.resolveClientId(auth.getName());
        return ApiResponse.of(orderService.createOrder(clientId, request));
    }

    // -------------------------------------------------------------------------
    // List (paginated JSON or CSV download)
    // -------------------------------------------------------------------------

    /**
     * Lists own orders as a paginated JSON response (default content type).
     *
     * @param status optional status filter
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List own orders",
            description = "Returns the authenticated client's orders, newest first. " +
                          "Supports optional ?status= filter. " +
                          "Send Accept: text/csv to download all matching orders as a CSV file " +
                          "(pagination is ignored for CSV).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "OK — paginated JSON or CSV file depending on Accept header",
                    content = {
                            @Content(mediaType = "application/json"),
                            @Content(mediaType = "text/csv")
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a CLIENT")
    })
    public PagedResponse<OrderSummaryDto> listOrders(
            @RequestParam(required = false)
            @Parameter(description = "Filter by order status") OrderStatus status,

            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,

            Authentication auth) {

        UUID clientId = orderService.resolveClientId(auth.getName());
        return PagedResponse.of(orderService.listOrders(clientId, status, pageable));
    }

    /**
     * Downloads all own orders as a CSV attachment.
     *
     * <p>Pagination parameters are ignored — all matching orders are included.
     * Spring MVC routes here automatically when the client sends {@code Accept: text/csv};
     * the response is serialised by {@link com.example.wms.order.export.OrderCsvMessageConverter}.
     *
     * @param status optional status filter
     */
    @GetMapping(produces = "text/csv")
    @Operation(hidden = true)
    public List<OrderSummaryDto> listOrdersCsv(
            @RequestParam(required = false) OrderStatus status,
            Authentication auth) {

        UUID clientId = orderService.resolveClientId(auth.getName());
        return orderService.listOrders(clientId, status, Pageable.unpaged()).getContent();
    }

    // -------------------------------------------------------------------------
    // Get by ID
    // -------------------------------------------------------------------------

    /**
     * Returns the full detail view of a single order, including all line items.
     *
     * <p>Returns HTTP 404 when the order does not exist or belongs to a different
     * client — both cases are intentionally indistinguishable.
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get order detail",
            description = "Returns the full order including all line items. " +
                          "Only the owning CLIENT may access this endpoint.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found or not owned by caller")
    })
    public ApiResponse<OrderDto> getOrder(@PathVariable UUID id, Authentication auth) {
        UUID clientId = orderService.resolveClientId(auth.getName());
        return ApiResponse.of(orderService.getOrder(id, clientId));
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Replaces the item list of an existing order.
     *
     * <p>Only allowed when the order is in {@code CREATED} or {@code DECLINED} status.
     * This is a full replacement — existing items are removed and replaced with the
     * items in the request body.
     */
    @PatchMapping("/{id}")
    @Operation(
            summary = "Update order items",
            description = "Full replacement of order items. " +
                          "Only allowed in CREATED or DECLINED status. " +
                          "Returns 422 if the order is in any other status.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found or not owned by caller"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Order is not in CREATED or DECLINED status")
    })
    public ApiResponse<OrderDto> updateOrder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderRequest request,
            Authentication auth) {

        UUID clientId = orderService.resolveClientId(auth.getName());
        return ApiResponse.of(orderService.updateOrder(id, clientId, request));
    }

    // -------------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    /**
     * Submits an order for manager approval, transitioning it to {@code AWAITING_APPROVAL}.
     *
     * <h2>Idempotency</h2>
     * The {@code Idempotency-Key} header is required. Duplicate requests with the same
     * key return the original response without re-executing the transition (24-hour TTL).
     *
     * @param id             the order UUID
     * @param idempotencyKey unique client-generated key to prevent duplicate submissions
     */
    @PostMapping("/{id}/submit")
    @Operation(
            summary = "Submit order for approval",
            description = "Transitions the order from CREATED or DECLINED to AWAITING_APPROVAL. " +
                          "Requires the Idempotency-Key header. " +
                          "Duplicate calls with the same key within 24 hours return the original response.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order submitted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or blank Idempotency-Key header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found or not owned by caller"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Order is not in a submittable status")
    })
    public ApiResponse<OrderDto> submitOrder(
            @PathVariable UUID id,

            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "Client-generated UUID to ensure exactly-once submission",
                       required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            String idempotencyKey,

            Authentication auth) {

        UUID clientId = orderService.resolveClientId(auth.getName());
        return ApiResponse.of(orderService.submitOrder(id, clientId, idempotencyKey));
    }

    // -------------------------------------------------------------------------
    // Cancel
    // -------------------------------------------------------------------------

    /**
     * Cancels an order owned by the authenticated client.
     *
     * <p>Valid from {@code CREATED}, {@code AWAITING_APPROVAL}, {@code APPROVED}, and
     * {@code DECLINED} statuses. Returns HTTP 409 if the order is in
     * {@code UNDER_DELIVERY}, {@code FULFILLED}, or {@code CANCELED}.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Cancel order",
            description = "Cancels the order. Allowed from CREATED, AWAITING_APPROVAL, APPROVED, " +
                          "and DECLINED statuses. Returns 409 for terminal or in-delivery orders.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Order cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found or not owned by caller"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Order cannot be cancelled from its current status")
    })
    public void cancelOrder(@PathVariable UUID id, Authentication auth) {
        UUID clientId = orderService.resolveClientId(auth.getName());
        orderService.cancelOrder(id, clientId);
    }

    // -------------------------------------------------------------------------
    // Audit log
    // -------------------------------------------------------------------------

    /**
     * Returns the complete status-transition history for the given order.
     *
     * <p>Returns HTTP 404 when the order does not exist or belongs to a different
     * client — both cases are intentionally indistinguishable.
     */
    @GetMapping("/{id}/audit")
    @Operation(
            summary = "Get order audit trail",
            description = "Returns the complete status-transition history for an order. " +
                          "Only the owning CLIENT may access this endpoint.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Audit trail returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found or not owned by caller")
    })
    public List<AuditLogEntryDto> getAuditLog(@PathVariable UUID id, Authentication auth) {
        return orderAuditService.getAuditLog(id, auth.getName());
    }
}

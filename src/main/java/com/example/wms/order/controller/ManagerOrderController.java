package com.example.wms.order.controller;

import com.example.wms.common.dto.ApiResponse;
import com.example.wms.common.dto.PagedResponse;
import com.example.wms.order.dto.DeclineOrderRequest;
import com.example.wms.order.dto.OrderDto;
import com.example.wms.order.dto.OrderSummaryDto;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Warehouse manager order endpoints — accessible to users with the {@code WAREHOUSE_MANAGER} role only.
 *
 * <h2>Security</h2>
 * <p>URL-level role enforcement is applied in {@code SecurityConfig}:
 * {@code /api/manager/**} → {@code hasRole("WAREHOUSE_MANAGER")}.
 * Managers have unrestricted visibility across all orders — no ownership check is applied.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Browse the full order queue (all clients, optional status filter)</li>
 *   <li>Inspect any individual order</li>
 *   <li>Approve or decline orders that are awaiting approval</li>
 * </ul>
 *
 * <p>Delivery scheduling endpoints live in {@code DeliveryController}
 * and are added in TASK-037.
 */
@RestController
@RequestMapping("/api/manager/orders")
@RequiredArgsConstructor
@Tag(name = "Manager — Orders", description = "Order review and approval endpoints (WAREHOUSE_MANAGER role only)")
@SecurityRequirement(name = "bearerAuth")
public class ManagerOrderController {

    private final OrderService orderService;

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of all orders across all clients, with an optional
     * status filter. Defaults to submission-date descending so the oldest pending
     * orders surface first when sorted ascending by the caller.
     *
     * @param status   optional status filter; omit to see all statuses
     * @param pageable pagination and sort parameters
     */
    @GetMapping
    @Operation(
            summary = "List all orders",
            description = "Returns a paginated view of all orders across all clients. " +
                          "Use the optional `status` query parameter to filter by order status " +
                          "(e.g. `AWAITING_APPROVAL` to see the approval queue). " +
                          "Default sort is submission date descending.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a WAREHOUSE_MANAGER")
    })
    public PagedResponse<OrderSummaryDto> listOrders(
            @RequestParam(required = false)
            @Parameter(description = "Filter by order status. Omit to return all statuses.")
            OrderStatus status,

            @ParameterObject
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return PagedResponse.of(orderService.listManagerOrders(status, pageable));
    }

    // -------------------------------------------------------------------------
    // Get by ID
    // -------------------------------------------------------------------------

    /**
     * Returns the full detail view of any order, including all line items.
     *
     * <p>Unlike the client endpoint, managers may view any order regardless of ownership.
     *
     * @param id the order UUID
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get order detail",
            description = "Returns the full order including all line items and current status. " +
                          "Available for any order — no ownership restriction applies to managers.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a WAREHOUSE_MANAGER"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ApiResponse<OrderDto> getOrder(@PathVariable UUID id) {
        return ApiResponse.of(orderService.getManagerOrder(id));
    }

    // -------------------------------------------------------------------------
    // Approve
    // -------------------------------------------------------------------------

    /**
     * Approves an order, transitioning it from {@code AWAITING_APPROVAL} → {@code APPROVED}.
     *
     * <p>Returns HTTP 409 if the order is not currently in {@code AWAITING_APPROVAL}.
     * The manager's username is recorded in the audit log for traceability.
     *
     * @param id   the order UUID
     * @param auth the authenticated manager principal
     */
    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Approve order",
            description = "Transitions the order from AWAITING_APPROVAL to APPROVED. " +
                          "The approving manager's username is recorded in the audit trail. " +
                          "Returns 409 if the order is not in AWAITING_APPROVAL status.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order approved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a WAREHOUSE_MANAGER"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Order is not in AWAITING_APPROVAL status")
    })
    public ApiResponse<OrderDto> approveOrder(@PathVariable UUID id, Authentication auth) {
        return ApiResponse.of(orderService.approveOrder(id, auth.getName()));
    }

    // -------------------------------------------------------------------------
    // Decline
    // -------------------------------------------------------------------------

    /**
     * Declines an order, transitioning it from {@code AWAITING_APPROVAL} → {@code DECLINED}.
     *
     * <p>The decline reason is mandatory and is stored on the order so the client can
     * see why their submission was rejected. A declined order may be updated and
     * re-submitted by the client (no retry limit is enforced).
     *
     * @param id      the order UUID
     * @param request the decline body containing the mandatory reason
     * @param auth    the authenticated manager principal
     */
    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Decline order",
            description = "Transitions the order from AWAITING_APPROVAL to DECLINED. " +
                          "The `reason` field is required and stored on the order — the client will see it. " +
                          "A declined order can be updated and re-submitted by the client. " +
                          "Returns 409 if the order is not in AWAITING_APPROVAL status.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order declined"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure — reason is blank or missing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a WAREHOUSE_MANAGER"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Order is not in AWAITING_APPROVAL status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Reason is blank (business rule violation)")
    })
    public ApiResponse<OrderDto> declineOrder(
            @PathVariable UUID id,
            @Valid @RequestBody DeclineOrderRequest request,
            Authentication auth) {

        return ApiResponse.of(orderService.declineOrder(id, auth.getName(), request.reason()));
    }
}

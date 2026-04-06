package com.example.wms.delivery.controller;

import com.example.wms.common.dto.ApiResponse;
import com.example.wms.delivery.dto.DeliveryDto;
import com.example.wms.delivery.dto.EligibleDaysResponse;
import com.example.wms.delivery.dto.ScheduleDeliveryRequest;
import com.example.wms.delivery.service.DeliveryService;
import com.example.wms.delivery.service.EligibleDayCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Delivery scheduling endpoints for warehouse managers.
 *
 * <h2>Security</h2>
 * URL-level protection is applied in {@code SecurityConfig}:
 * {@code /api/manager/**} → {@code hasRole("WAREHOUSE_MANAGER")}.
 * No additional method-level annotation is required.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Manager calls {@code GET /{id}/eligible-delivery-days} to retrieve valid dates.
 *       Results are cached in Redis for 5 minutes per order.</li>
 *   <li>Manager calls {@code POST /{id}/schedule} with a chosen date and one or more
 *       truck IDs. All business rules are re-validated server-side regardless of the
 *       eligible-days cache.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/manager/orders")
@RequiredArgsConstructor
@Tag(name = "Manager — Delivery", description = "Delivery scheduling endpoints (WAREHOUSE_MANAGER role only)")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {

    private final EligibleDayCalculator eligibleDayCalculator;
    private final DeliveryService       deliveryService;

    // -------------------------------------------------------------------------
    // GET eligible delivery days
    // -------------------------------------------------------------------------

    /**
     * Returns the list of weekday dates on which the given APPROVED order can be
     * delivered within the configured delivery window.
     *
     * <p>A date is eligible when at least one combination of active trucks has
     * combined volume &gt;= the order's required volume and no selected truck is
     * already booked on that date. Results are cached per order for 5 minutes.
     *
     * @param id   UUID of an approved order
     * @param auth authenticated manager principal (unused here, present for future audit)
     */
    @GetMapping("/{id}/eligible-delivery-days")
    @Operation(
            summary = "Get eligible delivery days",
            description = """
                    Returns an ordered list of weekday dates within the delivery window on which
                    the order can be physically delivered.

                    A date is eligible when:
                    - It is a weekday (Mon–Fri)
                    - It falls within the configured window (default 14 calendar days from tomorrow, max 30)
                    - The combined container volume of all active trucks NOT already booked on that date
                      is >= the order's total required volume (Σ packageVolume × requestedQuantity)

                    The result is cached in Redis for 5 minutes. The cache is evicted if any truck is
                    modified/deactivated or when a delivery is scheduled for this order.

                    Returns 409 if the order is not in APPROVED status.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK — list of eligible dates (may be empty if no trucks have sufficient capacity)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a WAREHOUSE_MANAGER"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Order is not in APPROVED status")
    })
    public ApiResponse<EligibleDaysResponse> getEligibleDeliveryDays(
            @PathVariable
            @Parameter(description = "UUID of the APPROVED order", required = true)
            UUID id,
            Authentication auth) {

        return ApiResponse.of(new EligibleDaysResponse(eligibleDayCalculator.getEligibleDays(id)));
    }

    // -------------------------------------------------------------------------
    // POST schedule delivery
    // -------------------------------------------------------------------------

    /**
     * Schedules a delivery for an approved order and transitions it to
     * {@code UNDER_DELIVERY}.
     *
     * <p>All business rules are validated server-side regardless of what the
     * eligible-days endpoint previously returned. A 422 is returned for any
     * constraint violation with a descriptive message identifying the root cause.
     *
     * <p>On success, inventory stock is decremented for each order item within the
     * same transaction. If any decrement fails (insufficient stock), the entire
     * operation rolls back and the order remains {@code APPROVED}.
     *
     * @param id      UUID of the order to schedule (must be in APPROVED status)
     * @param request the delivery date and the truck UUIDs to assign
     * @param auth    the authenticated manager (username recorded in delivery + audit log)
     */
    @PostMapping("/{id}/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Schedule delivery",
            description = """
                    Schedules a delivery for an APPROVED order and transitions it to UNDER_DELIVERY.

                    **Validations (all return 422 on failure):**
                    - Date must be a weekday (Mon–Fri)
                    - Date must fall within the configured delivery window
                      (tomorrow to tomorrow + delivery_window_days, max 30 days)
                    - All specified trucks must exist (404 if any are missing)
                    - All specified trucks must be active — inactive/deactivated trucks return 422
                    - No specified truck may already be assigned to another delivery on the chosen date
                    - Combined container volume of all specified trucks must be >= the order's
                      total required volume (Σ packageVolume × requestedQuantity)

                    **Side effects on success:**
                    - Delivery record created; trucks linked
                    - Order transitions APPROVED → UNDER_DELIVERY (with audit log entry)
                    - Inventory stock decremented for each order item (atomic — rolls back on failure)
                    - delivery-eligible Redis cache evicted for this order

                    **Soft warnings (non-blocking):**
                    - If any order item's deadlineDate precedes the chosen delivery date, the response
                      includes `hasDeadlineWarning: true` and the names of affected items.
                      Scheduling is NOT blocked.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Delivery scheduled — order is now UNDER_DELIVERY"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure — request body is missing required fields"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — caller is not a WAREHOUSE_MANAGER"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found, or one or more truck IDs not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Order is not in APPROVED status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = """
                            Business rule violation. Possible causes (errorCode in body):
                            DELIVERY_DATE_NOT_WEEKDAY — requested date falls on a weekend,
                            DELIVERY_DATE_OUT_OF_WINDOW — date is outside the allowed scheduling window,
                            TRUCK_INACTIVE — one or more trucks have been deactivated,
                            TRUCK_ALREADY_BOOKED — one or more trucks are already assigned to another delivery on this date,
                            INSUFFICIENT_TRUCK_VOLUME — combined truck volume is less than the order's required volume,
                            INSUFFICIENT_STOCK — inventory stock is insufficient for one or more order items (rare; rolls back)
                            """)
    })
    public ApiResponse<DeliveryDto> scheduleDelivery(
            @PathVariable
            @Parameter(description = "UUID of the APPROVED order to schedule", required = true)
            UUID id,

            @Valid @RequestBody ScheduleDeliveryRequest request,

            Authentication auth) {

        return ApiResponse.of(deliveryService.scheduleDelivery(id, request, auth.getName()));
    }
}

package com.example.wms.delivery.service;

import com.example.wms.common.exception.BusinessException;
import com.example.wms.config.service.SystemConfigService;
import com.example.wms.delivery.dto.DeliveryDto;
import com.example.wms.delivery.dto.ScheduleDeliveryRequest;
import com.example.wms.delivery.model.Delivery;
import com.example.wms.delivery.model.DeliveryTruck;
import com.example.wms.delivery.repository.DeliveryRepository;
import com.example.wms.delivery.repository.DeliveryTruckRepository;
import com.example.wms.inventory.service.InventoryService;
import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderItem;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.repository.OrderRepository;
import com.example.wms.order.statemachine.OrderStateMachine;
import com.example.wms.truck.model.Truck;
import com.example.wms.truck.repository.TruckRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles delivery scheduling for approved warehouse orders.
 *
 * <h2>Scheduling rules (enforced by this service)</h2>
 * <ol>
 *   <li>The order must be in {@code APPROVED} status.</li>
 *   <li>The requested date must be a weekday (Monday–Friday).</li>
 *   <li>The date must fall within the configured delivery window
 *       ({@code delivery_window_days} from {@code system_config}, capped at 30).</li>
 *   <li>Every specified truck must exist and be active ({@code active=true}).</li>
 *   <li>No specified truck may already be assigned to another delivery on the chosen date
 *       (one delivery per truck per day).</li>
 *   <li>The combined {@code container_volume} of all specified trucks must be &gt;=
 *       the order's total required volume ({@code Σ packageVolume × requestedQuantity}).</li>
 * </ol>
 *
 * <h2>Transaction</h2>
 * All mutations in {@link #scheduleDelivery} execute in a single transaction:
 * <ul>
 *   <li>INSERT into {@code deliveries}</li>
 *   <li>INSERT into {@code delivery_trucks} (one row per truck)</li>
 *   <li>Order status transition to {@code UNDER_DELIVERY} + audit log write</li>
 *   <li>Inventory stock decrements (one atomic UPDATE per order item)</li>
 * </ul>
 * A failure at any point rolls back the entire transaction.
 *
 * <h2>Cache</h2>
 * {@link #scheduleDelivery} evicts {@code delivery-eligible::{orderId}} after a
 * successful commit so the now-obsolete eligible-days result is not served again.
 * The {@code sla-report} cache is evicted by {@link OrderStateMachine#transition}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final String CONFIG_KEY_WINDOW  = "delivery_window_days";
    private static final int    DEFAULT_WINDOW_DAYS = 14;
    private static final int    MAX_WINDOW_DAYS     = 30;

    private final OrderRepository        orderRepository;
    private final TruckRepository        truckRepository;
    private final DeliveryRepository     deliveryRepository;
    private final DeliveryTruckRepository deliveryTruckRepository;
    private final InventoryService       inventoryService;
    private final OrderStateMachine      orderStateMachine;
    private final SystemConfigService    systemConfigService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Schedules a delivery for an approved order and transitions it to
     * {@code UNDER_DELIVERY}.
     *
     * <p>All business-rule validations are performed before any state change.
     * The entire operation is atomic — a failure at any stage rolls back the
     * transaction and leaves the order in {@code APPROVED} status.
     *
     * <p>The {@code delivery-eligible} Redis cache entry for this order is evicted
     * after a successful commit (the order is no longer schedulable once it is
     * {@code UNDER_DELIVERY}).
     *
     * @param orderId         UUID of an {@code APPROVED} order
     * @param request         schedule parameters (date + truck IDs)
     * @param managerUsername username of the authenticated {@code WAREHOUSE_MANAGER}
     * @return DTO describing the created delivery, including any soft deadline warnings
     * @throws EntityNotFoundException if the order or any truck is not found
     * @throws BusinessException       (HTTP 409) if the order is not in {@code APPROVED} status
     * @throws BusinessException       (HTTP 422) for any scheduling constraint violation
     */
    @Transactional
    @CacheEvict(cacheNames = "delivery-eligible", key = "#orderId")
    public DeliveryDto scheduleDelivery(UUID orderId,
                                        ScheduleDeliveryRequest request,
                                        String managerUsername) {

        // ── Phase 1: Load & validate order ───────────────────────────────────

        // Load with JOIN FETCH so orderItems + inventoryItems are available for
        // volume calculation without additional queries.
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.APPROVED) {
            throw new BusinessException(
                    "ORDER_NOT_APPROVED",
                    "Delivery can only be scheduled for APPROVED orders. "
                    + "Order '" + order.getOrderNumber() + "' is currently " + order.getStatus() + ".",
                    HttpStatus.CONFLICT);
        }

        // ── Phase 2: Validate the requested date ─────────────────────────────

        LocalDate requestedDate = request.date();

        if (isWeekend(requestedDate)) {
            throw new BusinessException(
                    "DELIVERY_DATE_NOT_WEEKDAY",
                    "Deliveries can only be scheduled on weekdays (Mon–Fri). "
                    + requestedDate + " is a " + requestedDate.getDayOfWeek() + ".");
        }

        int windowDays = Math.min(
                systemConfigService.getIntValue(CONFIG_KEY_WINDOW, DEFAULT_WINDOW_DAYS),
                MAX_WINDOW_DAYS);
        LocalDate windowStart = LocalDate.now().plusDays(1);           // tomorrow (earliest allowed)
        LocalDate windowEnd   = windowStart.plusDays(windowDays - 1);  // inclusive upper bound

        if (requestedDate.isBefore(windowStart) || requestedDate.isAfter(windowEnd)) {
            throw new BusinessException(
                    "DELIVERY_DATE_OUT_OF_WINDOW",
                    "Delivery date " + requestedDate + " is outside the allowed window ["
                    + windowStart + " – " + windowEnd + "]. "
                    + "Adjust the date or update delivery_window_days in system config.");
        }

        // ── Phase 3: Load and validate trucks ────────────────────────────────

        // Deduplicate: a manager might accidentally send the same truck ID twice.
        List<UUID> uniqueTruckIds = request.truckIds().stream().distinct().toList();

        List<Truck> trucks = truckRepository.findAllById(uniqueTruckIds);

        // 3a. All requested truck IDs must exist in the DB.
        if (trucks.size() < uniqueTruckIds.size()) {
            Set<UUID> foundIds = trucks.stream()
                    .map(Truck::getId)
                    .collect(Collectors.toSet());
            List<UUID> missingIds = uniqueTruckIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new EntityNotFoundException("Trucks not found: " + missingIds);
        }

        // 3b. All trucks must be active (not soft-deleted).
        List<String> inactivePlates = trucks.stream()
                .filter(t -> !t.isActive())
                .map(Truck::getLicensePlate)
                .toList();
        if (!inactivePlates.isEmpty()) {
            throw new BusinessException(
                    "TRUCK_INACTIVE",
                    "The following trucks are inactive and cannot be assigned to a delivery: "
                    + inactivePlates + ". Reactivate them or choose different trucks.");
        }

        // 3c. Each truck must be available on the requested date (one delivery per truck per day).
        //     Loaded in a single query to avoid N per-truck round-trips.
        Set<UUID> bookedTruckIds = deliveryTruckRepository.findAllByDeliveryDate(requestedDate)
                .stream()
                .map(dt -> dt.getId().getTruckId())
                .collect(Collectors.toSet());

        List<String> alreadyBookedPlates = trucks.stream()
                .filter(t -> bookedTruckIds.contains(t.getId()))
                .map(Truck::getLicensePlate)
                .toList();
        if (!alreadyBookedPlates.isEmpty()) {
            throw new BusinessException(
                    "TRUCK_ALREADY_BOOKED",
                    "The following trucks are already assigned to a delivery on " + requestedDate + ": "
                    + alreadyBookedPlates + ". Choose different trucks or a different date.");
        }

        // ── Phase 4: Validate volume ─────────────────────────────────────────

        BigDecimal requiredVolume  = computeOrderVolume(order);
        BigDecimal availableVolume = trucks.stream()
                .map(Truck::getContainerVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (availableVolume.compareTo(requiredVolume) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_TRUCK_VOLUME",
                    String.format(
                            "Combined truck volume (%.4f m³) is insufficient for order '%s' "
                            + "which requires %.4f m³. Add more trucks or choose trucks with "
                            + "greater capacity.",
                            availableVolume, order.getOrderNumber(), requiredVolume));
        }

        // ── Phase 5: Soft deadline warning (never blocks scheduling) ─────────

        List<String> itemsExceedingDeadline = computeDeadlineWarnings(order, requestedDate);
        if (!itemsExceedingDeadline.isEmpty()) {
            log.warn("Order '{}' scheduled for {} but the following items have earlier deadlines: {}",
                    order.getOrderNumber(), requestedDate, itemsExceedingDeadline);
        }

        // ── Phase 6: Persist delivery + truck assignments ─────────────────────

        // Build and save the Delivery first so Hibernate assigns its UUID.
        // DeliveryTruck.of() needs delivery.getId() for the composite PK.
        Delivery delivery = Delivery.builder()
                .order(order)
                .deliveryDate(requestedDate)
                .scheduledBy(managerUsername)
                .build();
        deliveryRepository.save(delivery);
        // delivery.getId() is now non-null (GenerationType.UUID assigns before INSERT).

        for (Truck truck : trucks) {
            delivery.getTruckAssignments().add(DeliveryTruck.of(delivery, truck));
        }
        // Truck assignments will be cascade-persisted at session flush time
        // (CascadeType.ALL + orphanRemoval on Delivery.truckAssignments).

        // ── Phase 7: Transition order → UNDER_DELIVERY ───────────────────────

        // OrderStateMachine requires Propagation.MANDATORY — it joins our transaction.
        // It also writes the audit log entry and evicts the sla-report cache.
        orderStateMachine.transition(order, OrderStatus.UNDER_DELIVERY, managerUsername, null);

        // ── Phase 8: Decrement inventory ─────────────────────────────────────

        // Per CLAUDE.md business rule: inventory is decremented ONLY at delivery
        // scheduling (APPROVED → UNDER_DELIVERY), never at order approval.
        // InventoryService.decrementStock() uses Propagation.REQUIRED → joins our tx.
        // If any decrement fails (insufficient stock), the entire transaction rolls back.
        for (OrderItem item : order.getOrderItems()) {
            inventoryService.decrementStock(
                    item.getInventoryItem().getId(),
                    item.getRequestedQuantity());
        }

        log.info("Delivery scheduled for order '{}' on {} using trucks {} by '{}'",
                order.getOrderNumber(), requestedDate,
                trucks.stream().map(Truck::getLicensePlate).toList(),
                managerUsername);

        return DeliveryDto.from(delivery, itemsExceedingDeadline);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Calculates the total volume this order requires for delivery.
     *
     * <p>Formula: {@code Σ (item.inventoryItem.packageVolume × item.requestedQuantity)}.
     * Items with a {@code null} package volume are treated as dimensionless (contribute 0).
     *
     * <p>The {@code inventoryItem} associations are already eagerly loaded by
     * {@code OrderRepository.findByIdWithItems()} — no additional queries.
     */
    private BigDecimal computeOrderVolume(Order order) {
        return order.getOrderItems().stream()
                .map(item -> {
                    BigDecimal vol = item.getInventoryItem().getPackageVolume();
                    if (vol == null || vol.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }
                    return vol.multiply(BigDecimal.valueOf(item.getRequestedQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Returns the names of order items whose {@code deadlineDate} precedes the
     * scheduled delivery date.
     *
     * <p>This is a <strong>soft warning only</strong> — the list is included in
     * the response for the manager's awareness but never blocks scheduling.
     */
    private List<String> computeDeadlineWarnings(Order order, LocalDate deliveryDate) {
        return order.getOrderItems().stream()
                .filter(item -> item.getDeadlineDate() != null
                        && deliveryDate.isAfter(item.getDeadlineDate()))
                .map(item -> item.getInventoryItem().getName())
                .toList();
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}

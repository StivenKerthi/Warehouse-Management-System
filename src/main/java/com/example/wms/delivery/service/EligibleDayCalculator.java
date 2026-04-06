package com.example.wms.delivery.service;

import com.example.wms.common.exception.BusinessException;
import com.example.wms.config.service.SystemConfigService;
import com.example.wms.delivery.repository.DeliveryTruckRepository;
import com.example.wms.order.model.Order;
import com.example.wms.order.model.OrderStatus;
import com.example.wms.order.repository.OrderRepository;
import com.example.wms.truck.model.Truck;
import com.example.wms.truck.repository.TruckRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the list of eligible delivery dates for a given APPROVED order.
 *
 * <h2>Eligibility rules</h2>
 * A calendar date is eligible when ALL of the following hold:
 * <ol>
 *   <li>It is a weekday (Monday–Friday).</li>
 *   <li>It falls within the delivery window: from tomorrow up to
 *       {@code delivery_window_days} calendar days ahead (max 30, from
 *       {@code system_config}).</li>
 *   <li>The combined {@code container_volume} of all active trucks that are
 *       NOT already assigned to another delivery on that date is &gt;=
 *       the order's total required volume ({@code Σ packageVolume × requestedQuantity}).
 *       At least one truck must be available even when the required volume is zero.</li>
 * </ol>
 *
 * <h2>Caching</h2>
 * Results are cached in Redis under cache {@code "delivery-eligible"}, keyed on
 * {@code orderId}, with a 5-minute TTL configured in {@link com.example.wms.config.RedisConfig}.
 * The cache is evicted:
 * <ul>
 *   <li>On any truck modification or soft-deletion — by {@code TruckService}
 *       ({@code @CacheEvict(cacheNames="delivery-eligible", allEntries=true)}).</li>
 *   <li>When a delivery is scheduled for this order — by {@code DeliveryService}
 *       ({@code @CacheEvict(cacheNames="delivery-eligible", key="#orderId")}).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EligibleDayCalculator {

    /** Hard ceiling on the delivery window regardless of what is stored in system_config. */
    static final int MAX_WINDOW_DAYS = 30;

    private static final String CONFIG_KEY_WINDOW = "delivery_window_days";
    private static final int    DEFAULT_WINDOW_DAYS = 14;

    private final OrderRepository        orderRepository;
    private final TruckRepository        truckRepository;
    private final DeliveryTruckRepository deliveryTruckRepository;
    private final SystemConfigService    systemConfigService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered list of eligible delivery dates for the given order.
     *
     * <p>The list is cached in Redis. Subsequent calls with the same {@code orderId}
     * return the cached result without touching the database until the TTL expires
     * or the cache is explicitly evicted.
     *
     * @param orderId UUID of an {@code APPROVED} order
     * @return non-null, possibly-empty, chronologically ordered list of eligible dates
     * @throws EntityNotFoundException if the order does not exist
     * @throws BusinessException       (HTTP 409) if the order is not in {@code APPROVED} status
     */
    @Cacheable(cacheNames = "delivery-eligible", key = "#orderId")
    @Transactional(readOnly = true)
    public List<LocalDate> getEligibleDays(UUID orderId) {

        // ── 1. Load order (with items + inventory in one JOIN FETCH query) ──
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.APPROVED) {
            throw new BusinessException(
                    "ORDER_NOT_APPROVED",
                    "Eligible delivery days can only be computed for APPROVED orders. "
                    + "Current status: " + order.getStatus(),
                    HttpStatus.CONFLICT);
        }

        // ── 2. Compute the total volume this order requires ──
        BigDecimal requiredVolume = computeOrderVolume(order);

        // ── 3. Determine the search window ──
        int windowDays = Math.min(
                systemConfigService.getIntValue(CONFIG_KEY_WINDOW, DEFAULT_WINDOW_DAYS),
                MAX_WINDOW_DAYS);

        LocalDate windowStart = LocalDate.now().plusDays(1); // tomorrow
        LocalDate windowEnd   = windowStart.plusDays(windowDays - 1);

        // ── 4. Load all active trucks once ──
        List<Truck> activeTrucks = truckRepository.findAllByActiveTrue();
        if (activeTrucks.isEmpty()) {
            log.warn("No active trucks — no eligible delivery dates for order {}", orderId);
            return List.of();
        }

        // ── 5. Pre-load booked-truck data for the entire window in ONE query ──
        //
        //  Key insight: DeliveryTruck's EmbeddedId already contains truckId —
        //  dt.getId().getTruckId() reads the PK directly with no lazy-load proxy hit.
        //  The JOIN FETCH on delivery (added to the range query) makes
        //  dt.getDelivery().getDeliveryDate() safe to call here.
        Map<LocalDate, Set<UUID>> bookedByDate = buildBookedTrucksMap(windowStart, windowEnd);

        // ── 6. Walk the window and evaluate each calendar day ──
        List<LocalDate> eligibleDays = new ArrayList<>();

        for (int offset = 0; offset < windowDays; offset++) {
            LocalDate candidate = windowStart.plusDays(offset);

            if (isWeekend(candidate)) {
                continue; // weekends are never eligible
            }

            List<Truck> availableOnDay = filterAvailableTrucks(
                    activeTrucks, bookedByDate.getOrDefault(candidate, Set.of()));

            if (availableOnDay.isEmpty()) {
                continue; // no trucks at all → can't deliver
            }

            BigDecimal availableVolume = totalVolume(availableOnDay);

            if (availableVolume.compareTo(requiredVolume) >= 0) {
                eligibleDays.add(candidate);
            }
        }

        log.debug("Order {} — {}/{} days eligible in {}-day window (required volume: {} m³)",
                orderId, eligibleDays.size(), windowDays, windowDays, requiredVolume);

        return Collections.unmodifiableList(eligibleDays);
    }

    // -------------------------------------------------------------------------
    // Package-private helpers (accessible from tests without reflection)
    // -------------------------------------------------------------------------

    /**
     * Calculates the total required delivery volume for an order.
     *
     * <p>Formula: {@code Σ (item.inventoryItem.packageVolume × item.requestedQuantity)}.
     * Items whose inventory entry has a {@code null} package volume contribute zero
     * (they are treated as dimensionless for scheduling purposes).
     */
    BigDecimal computeOrderVolume(Order order) {
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads all delivery-truck assignments within [{@code start}, {@code end}] in one
     * query and groups them by delivery date → set of booked truck UUIDs.
     *
     * <p>Using the embedded PK ({@code dt.getId().getTruckId()}) avoids loading the
     * full {@code Truck} entity for each row.
     */
    private Map<LocalDate, Set<UUID>> buildBookedTrucksMap(LocalDate start, LocalDate end) {
        return deliveryTruckRepository.findAllByDeliveryDateBetween(start, end)
                .stream()
                .collect(Collectors.groupingBy(
                        dt -> dt.getDelivery().getDeliveryDate(),   // delivery JOIN FETCH-ed
                        Collectors.mapping(
                                dt -> dt.getId().getTruckId(),       // no lazy-load: reads embedded PK
                                Collectors.toCollection(HashSet::new))));
    }

    /**
     * Returns the subset of {@code allActive} trucks that are not present in
     * {@code bookedIds}.
     */
    private List<Truck> filterAvailableTrucks(List<Truck> allActive, Set<UUID> bookedIds) {
        if (bookedIds.isEmpty()) {
            return allActive; // fast path — no allocations needed
        }
        return allActive.stream()
                .filter(t -> !bookedIds.contains(t.getId()))
                .toList();
    }

    /**
     * Sums {@code containerVolume} across the given trucks.
     *
     * <p>{@code containerVolume} is {@code NOT NULL} in the DB schema so no
     * null check is required here.
     */
    private BigDecimal totalVolume(List<Truck> trucks) {
        return trucks.stream()
                .map(Truck::getContainerVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}

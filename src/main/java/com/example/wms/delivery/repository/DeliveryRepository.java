package com.example.wms.delivery.repository;

import com.example.wms.delivery.model.Delivery;
import com.example.wms.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    /**
     * Checks whether a given truck is already assigned to any delivery on a
     * specific date. Used by {@code EligibleDayCalculator} and
     * {@code DeliveryService} to enforce the one-delivery-per-truck-per-day rule.
     *
     * @param truckId      truck to check
     * @param deliveryDate calendar date to check
     * @return true if the truck is booked on that date
     */
    @Query("""
            SELECT COUNT(dt) > 0
            FROM DeliveryTruck dt
            WHERE dt.truck.id = :truckId
              AND dt.delivery.deliveryDate = :deliveryDate
            """)
    boolean isTruckBookedOnDate(@Param("truckId") UUID truckId,
                                @Param("deliveryDate") LocalDate deliveryDate);

    /**
     * Returns all deliveries whose {@code deliveryDate} is on or before
     * {@code cutoffDate} and whose associated order has the given status.
     *
     * <p>Used by the nightly cron job ({@code FulfillmentScheduler}) to find
     * {@code UNDER_DELIVERY} orders whose delivery date has passed — these are
     * ready to be transitioned to {@code FULFILLED}.
     *
     * @param cutoffDate upper bound on delivery date (inclusive); typically {@code LocalDate.now()}
     * @param status     order status to filter by; typically {@code UNDER_DELIVERY}
     * @return list of matching deliveries; may be empty, never null
     */
    @Query("""
            SELECT d FROM Delivery d
            JOIN FETCH d.order o
            WHERE d.deliveryDate <= :cutoffDate
              AND o.status = :status
            """)
    List<Delivery> findByDeliveryDateLessThanEqualAndOrderStatus(
            @Param("cutoffDate") LocalDate cutoffDate,
            @Param("status") OrderStatus status);

    /**
     * Finds the delivery scheduled for a specific order, if any.
     * Used when returning order details that include delivery information.
     *
     * @param orderId the order's UUID
     * @return the delivery, or empty if not yet scheduled
     */
    Optional<Delivery> findByOrderId(UUID orderId);

    /**
     * Returns all deliveries on a specific date that involve a given truck.
     * Convenience query used by {@code DeliveryService} for availability checks
     * when the caller already knows the date and needs the full delivery context.
     *
     * @param truckId      truck to check
     * @param deliveryDate date to check
     * @return matching deliveries (0 or more)
     */
    @Query("""
            SELECT d FROM Delivery d
            JOIN d.truckAssignments dt
            WHERE dt.truck.id = :truckId
              AND d.deliveryDate = :deliveryDate
            """)
    List<Delivery> findByTruckIdAndDeliveryDate(@Param("truckId") UUID truckId,
                                                @Param("deliveryDate") LocalDate deliveryDate);
}

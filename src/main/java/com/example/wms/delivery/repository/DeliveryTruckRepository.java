package com.example.wms.delivery.repository;

import com.example.wms.delivery.model.DeliveryTruck;
import com.example.wms.delivery.model.DeliveryTruckId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


public interface DeliveryTruckRepository extends JpaRepository<DeliveryTruck, DeliveryTruckId> {

    /**
     * Returns all truck assignments for a specific delivery.
     *
     * @param deliveryId the delivery's UUID
     * @return list of assignments, may be empty
     */
    List<DeliveryTruck> findByDeliveryId(UUID deliveryId);

    /**
     * Returns all delivery assignments for a specific truck.
     * Useful for inspecting a truck's schedule.
     *
     * @param truckId the truck's UUID
     * @return list of assignments, may be empty
     */
    List<DeliveryTruck> findByTruckId(UUID truckId);

    /**
     * Returns all truck assignments for deliveries on a specific date.
     * Used by {@code EligibleDayCalculator} to determine which trucks are
     * already booked when computing eligible delivery days.
     *
     * @param deliveryDate the calendar date to check
     * @return list of assignments on that date
     */
    @Query("""
            SELECT dt FROM DeliveryTruck dt
            WHERE dt.delivery.deliveryDate = :deliveryDate
            """)
    List<DeliveryTruck> findAllByDeliveryDate(@Param("deliveryDate") LocalDate deliveryDate);

    /**
     * Returns all truck assignments for deliveries whose date falls within
     * [{@code startDate}, {@code endDate}] (both inclusive).
     *
     * <p>The {@code Delivery} association is JOIN FETCH-ed so callers can safely
     * call {@code dt.getDelivery().getDeliveryDate()} without triggering an
     * additional query per row.
     *
     * <p>Used by {@code EligibleDayCalculator} to load all booked-truck data for
     * an entire delivery window in a single database round-trip.
     *
     * @param startDate first date of the window (inclusive)
     * @param endDate   last date of the window (inclusive)
     * @return list of assignments; may be empty
     */
    @Query("""
            SELECT dt FROM DeliveryTruck dt
            JOIN FETCH dt.delivery d
            WHERE d.deliveryDate BETWEEN :startDate AND :endDate
            """)
    List<DeliveryTruck> findAllByDeliveryDateBetween(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);
}

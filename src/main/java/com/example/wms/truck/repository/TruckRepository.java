package com.example.wms.truck.repository;

import com.example.wms.truck.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TruckRepository extends JpaRepository<Truck, UUID> {

    boolean existsByChassisNumber(String chassisNumber);

    boolean existsByChassisNumberAndIdNot(String chassisNumber, UUID id);

    boolean existsByLicensePlate(String licensePlate);

    boolean existsByLicensePlateAndIdNot(String licensePlate, UUID id);

    /** Returns all trucks that have not been soft-deleted. */
    List<Truck> findAllByActiveTrue();

    /** Count of non-soft-deleted trucks. Used by the SLA report. */
    @Query("SELECT COUNT(t) FROM Truck t WHERE t.active = true")
    long countActiveTrucks();

    /**
     * Sum of container volumes for all active trucks.
     * Returns {@code 0.00} when no active trucks exist (COALESCE guard).
     * Used by the SLA report to show total available fleet capacity.
     */
    @Query("SELECT COALESCE(SUM(t.containerVolume), 0) FROM Truck t WHERE t.active = true")
    BigDecimal sumActiveContainerVolume();
}

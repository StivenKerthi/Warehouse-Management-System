package com.example.wms.truck.repository;

import com.example.wms.truck.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TruckRepository extends JpaRepository<Truck, UUID> {

    boolean existsByChassisNumber(String chassisNumber);

    boolean existsByChassisNumberAndIdNot(String chassisNumber, UUID id);

    boolean existsByLicensePlate(String licensePlate);

    boolean existsByLicensePlateAndIdNot(String licensePlate, UUID id);
}

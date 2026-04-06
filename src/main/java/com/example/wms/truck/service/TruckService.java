package com.example.wms.truck.service;

import com.example.wms.common.exception.BusinessException;
import com.example.wms.truck.dto.CreateTruckRequest;
import com.example.wms.truck.dto.TruckDto;
import com.example.wms.truck.dto.UpdateTruckRequest;
import com.example.wms.truck.mapper.TruckMapper;
import com.example.wms.truck.model.Truck;
import com.example.wms.truck.repository.TruckRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TruckService {

    private final TruckRepository repository;
    private final TruckMapper     mapper;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public TruckDto create(CreateTruckRequest request) {
        if (repository.existsByChassisNumber(request.chassisNumber())) {
            throw new BusinessException("CHASSIS_NUMBER_TAKEN",
                    "Chassis number '" + request.chassisNumber() + "' is already registered.",
                    HttpStatus.CONFLICT);
        }
        if (repository.existsByLicensePlate(request.licensePlate())) {
            throw new BusinessException("LICENSE_PLATE_TAKEN",
                    "License plate '" + request.licensePlate() + "' is already registered.",
                    HttpStatus.CONFLICT);
        }

        Truck truck = Truck.builder()
                .chassisNumber(request.chassisNumber())
                .licensePlate(request.licensePlate())
                .containerVolume(request.containerVolume())
                .active(true)
                .build();

        return mapper.toDto(repository.save(truck));
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TruckDto findById(UUID id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<TruckDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDto);
    }

    // -------------------------------------------------------------------------
    // Update — evicts delivery eligibility cache for all orders
    // -------------------------------------------------------------------------

    /**
     * Updates a truck and evicts all cached delivery-eligible-days entries.
     *
     * <p>Any change to the fleet (volume, active status) invalidates the eligible
     * delivery dates that were computed for pending orders, so all entries must
     * be flushed. The cache is re-populated lazily on next request.
     */
    @Transactional
    @CacheEvict(cacheNames = "delivery-eligible", allEntries = true)
    public TruckDto update(UUID id, UpdateTruckRequest request) {
        Truck truck = getOrThrow(id);

        if (request.chassisNumber() != null) {
            if (!request.chassisNumber().equals(truck.getChassisNumber())
                    && repository.existsByChassisNumberAndIdNot(request.chassisNumber(), id)) {
                throw new BusinessException("CHASSIS_NUMBER_TAKEN",
                        "Chassis number '" + request.chassisNumber() + "' is already registered.",
                        HttpStatus.CONFLICT);
            }
            truck.setChassisNumber(request.chassisNumber());
        }

        if (request.licensePlate() != null) {
            if (!request.licensePlate().equals(truck.getLicensePlate())
                    && repository.existsByLicensePlateAndIdNot(request.licensePlate(), id)) {
                throw new BusinessException("LICENSE_PLATE_TAKEN",
                        "License plate '" + request.licensePlate() + "' is already registered.",
                        HttpStatus.CONFLICT);
            }
            truck.setLicensePlate(request.licensePlate());
        }

        if (request.containerVolume() != null) {
            truck.setContainerVolume(request.containerVolume());
        }

        if (request.active() != null) {
            truck.setActive(request.active());
        }

        return mapper.toDto(repository.save(truck));
    }

    // -------------------------------------------------------------------------
    // Delete (soft) — evicts delivery eligibility cache for all orders
    // -------------------------------------------------------------------------

    /**
     * Soft-deletes a truck (sets {@code active=false}) and evicts all cached
     * delivery-eligible-days entries.
     *
     * <p>Removing a truck from the active fleet changes which trucks are available
     * for scheduling, so previously cached eligible-days results are stale.
     */
    @Transactional
    @CacheEvict(cacheNames = "delivery-eligible", allEntries = true)
    public void delete(UUID id) {
        Truck truck = getOrThrow(id);
        truck.setActive(false);
        repository.save(truck);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Truck getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Truck not found: " + id));
    }
}

package com.example.wms.truck.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * JPA entity for the {@code trucks} table.
 *
 * <p>Schema owned by Flyway (V4__create_trucks.sql). Hibernate runs in
 * {@code ddl-auto=validate} — all fields must match DB columns exactly.
 *
 * <p>Trucks are soft-deleted ({@code active=false}) so delivery history referencing
 * them is preserved. Inactive trucks are excluded from delivery scheduling.
 */
@Entity
@Table(name = "trucks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Truck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Vehicle Identification Number or equivalent — unique across the fleet. */
    @Column(name = "chassis_number", nullable = false, unique = true, length = 50)
    private String chassisNumber;

    /** Road-legal registration plate — unique within the fleet. */
    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;

    /** Total usable cargo space in cubic metres (m³). Must be > 0. */
    @Column(name = "container_volume", nullable = false, precision = 10, scale = 4)
    private BigDecimal containerVolume;

    /** Soft-delete flag. Inactive trucks are excluded from new delivery scheduling. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

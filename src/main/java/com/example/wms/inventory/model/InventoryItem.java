package com.example.wms.inventory.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * JPA entity for the {@code inventory_items} table.
 *
 * <p>Schema owned by Flyway (V3__create_inventory_items.sql).
 * Hibernate runs in {@code ddl-auto=validate} — all fields must match DB columns exactly.
 *
 * <p>{@code quantity} must never go below zero. The DB has a CHECK constraint as a final
 * safety net, but the service layer enforces this rule before any write.
 */
@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    /** Current on-hand stock level. Decremented only at delivery scheduling. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Current list price in system currency. May be null at creation. */
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Volume per single unit in cubic metres (m³). Used for truck capacity validation. */
    @Column(name = "package_volume", precision = 10, scale = 4)
    private BigDecimal packageVolume;

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

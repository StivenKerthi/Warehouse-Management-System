package com.example.wms.delivery.model;

import com.example.wms.order.model.Order;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the {@code deliveries} table.
 *
 * <p>Schema owned by Flyway (V8__create_deliveries.sql). Hibernate runs in
 * {@code ddl-auto=validate} — all fields must match DB columns exactly.
 *
 * <p>One delivery per order is enforced by the unique constraint on
 * {@code order_id}. Mapped as {@code @OneToOne} from this side.
 *
 * <p>Assigned trucks are navigable via {@link #truckAssignments} (the
 * {@code DeliveryTruck} join entities). Volume validation and the
 * one-truck-per-day rule are enforced by {@code DeliveryService} before
 * persisting this entity.
 */
@Entity
@Table(name = "deliveries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The order this delivery fulfils. One-to-one; the unique constraint on
     * {@code deliveries.order_id} is the DB-level enforcement.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_deliveries_order"))
    private Order order;

    /**
     * Target delivery date. Must be a weekday within the configured delivery
     * window. Validated by {@code EligibleDayCalculator} before reaching here.
     * Stored as {@code DATE} — no time zone component needed.
     */
    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    /** Timestamp when the manager scheduled this delivery. */
    @Column(name = "scheduled_at", nullable = false, updatable = false)
    private OffsetDateTime scheduledAt;

    /**
     * Username of the {@code WAREHOUSE_MANAGER} who created this delivery.
     * Stored as plain VARCHAR (not a FK) so the record survives if the user
     * is deactivated.
     */
    @Column(name = "scheduled_by", nullable = false, length = 100, updatable = false)
    private String scheduledBy;

    /**
     * Truck assignments for this delivery. Eagerly loaded — the list is small
     * (typically 1–3 trucks) and always needed when a delivery is inspected.
     */
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<DeliveryTruck> truckAssignments = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (this.scheduledAt == null) {
            this.scheduledAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}

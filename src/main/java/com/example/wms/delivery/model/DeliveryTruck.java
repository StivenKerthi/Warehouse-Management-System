package com.example.wms.delivery.model;

import com.example.wms.truck.model.Truck;
import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity for the {@code delivery_trucks} join table.
 *
 * <p>Schema owned by Flyway (V9__create_delivery_trucks.sql). Hibernate runs in
 * {@code ddl-auto=validate} — all fields must match DB columns exactly.
 *
 * <p>The composite primary key {@link DeliveryTruckId} prevents the same truck
 * from being assigned to the same delivery twice. The one-truck-per-day rule
 * (a truck may only appear in one delivery per calendar day) is enforced by
 * {@code DeliveryService} before persisting.
 */
@Entity
@Table(name = "delivery_trucks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTruck {

    @EmbeddedId
    private DeliveryTruckId id;

    /**
     * The delivery this assignment belongs to.
     * {@code @MapsId} links the {@code deliveryId} column of the embedded PK
     * to this association, so both the FK column and the PK component are kept
     * in sync automatically.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("deliveryId")
    @JoinColumn(name = "delivery_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_delivery_trucks_delivery"))
    private Delivery delivery;

    /**
     * The truck assigned to this delivery.
     * {@code @MapsId} links the {@code truckId} column of the embedded PK
     * to this association.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("truckId")
    @JoinColumn(name = "truck_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_delivery_trucks_truck"))
    private Truck truck;

    /** Convenience factory — ensures PK is always constructed from the two entities. */
    public static DeliveryTruck of(Delivery delivery, Truck truck) {
        return DeliveryTruck.builder()
                .id(new DeliveryTruckId(delivery.getId(), truck.getId()))
                .delivery(delivery)
                .truck(truck)
                .build();
    }
}

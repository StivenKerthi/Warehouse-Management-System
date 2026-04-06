package com.example.wms.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for the {@code delivery_trucks} join table.
 *
 * <p>Must implement {@link Serializable} and provide correct
 * {@link #equals}/{@link #hashCode} — required by the JPA spec for
 * {@code @EmbeddedId} types so the persistence context can deduplicate
 * entities in the first-level cache.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DeliveryTruckId implements Serializable {

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "truck_id", nullable = false)
    private UUID truckId;
}

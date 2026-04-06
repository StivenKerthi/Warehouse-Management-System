package com.example.wms.order.model;

import com.example.wms.inventory.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the {@code order_items} table.
 *
 * <p>Schema owned by Flyway (V6__create_order_items.sql). Hibernate runs in
 * {@code ddl-auto=validate} — all fields must match DB columns exactly.
 *
 * <p>{@code unitPriceSnapshot} is copied from {@link InventoryItem#getUnitPrice()}
 * at the moment the parent {@link Order} is created. It is never updated retroactively,
 * so price changes on the inventory item do not affect existing orders.
 *
 * <p>{@code deadlineDate} is a soft warning only — the manager is flagged when
 * the scheduled delivery date exceeds it, but scheduling is never hard-blocked.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Parent order. Owning side of the bidirectional relationship. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_order"))
    private Order order;

    /** The inventory item being requested. Never null; deletion restricted at DB level. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_inventory_item"))
    private InventoryItem inventoryItem;

    /** Number of units requested. Must be > 0 (enforced by DB CHECK constraint). */
    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    /**
     * Price per unit copied from {@link InventoryItem#getUnitPrice()} at order creation time.
     * Never updated retroactively. Must be >= 0 (enforced by DB CHECK constraint).
     */
    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    /**
     * Optional client-requested delivery deadline.
     * Treated as a soft warning — exceeding it flags the manager but does not block scheduling.
     */
    @Column(name = "deadline_date")
    private LocalDate deadlineDate;
}

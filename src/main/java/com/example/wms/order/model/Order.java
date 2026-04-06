package com.example.wms.order.model;

import com.example.wms.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the {@code orders} table.
 *
 * <p>Schema owned by Flyway (V5__create_orders.sql). Hibernate runs in
 * {@code ddl-auto=validate} — all fields must match DB columns exactly.
 *
 * <p>{@code orderNumber} is assigned by {@link com.example.wms.common.util.OrderNumberGenerator}
 * before the entity is first persisted. It must never be changed after creation.
 *
 * <p>{@code status} is managed exclusively by {@code OrderStateMachine.java}.
 * Never call {@link #setStatus} directly in service or controller code.
 *
 * <p>{@code orderItems} are lazily loaded. Always access them within an active
 * transaction to avoid {@code LazyInitializationException}.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Human-readable identifier. Generated once on first save via
     * {@link com.example.wms.common.util.OrderNumberGenerator#next()}.
     * Format: {@code ORD-YYYY-NNNNN}.
     */
    @Column(name = "order_number", nullable = false, unique = true, length = 20, updatable = false)
    private String orderNumber;

    /** The client who owns this order. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_orders_client"))
    private User client;

    /**
     * Current lifecycle state. Managed exclusively by {@code OrderStateMachine}.
     * Stored as a plain VARCHAR — no PostgreSQL enum type so future states can
     * be added via a simple migration (no {@code ALTER TYPE ... ADD VALUE} required).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    /** Populated by the manager when declining an order. Null in all other states. */
    @Column(name = "decline_reason", length = 1000)
    private String declineReason;

    /** Set when the client submits the order (CREATED → AWAITING_APPROVAL). Null until then. */
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Line items belonging to this order. Lazily loaded.
     * {@code CascadeType.ALL} + {@code orphanRemoval} ensures items are persisted
     * and deleted together with the order.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = OrderStatus.CREATED;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

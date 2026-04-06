package com.example.wms.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * JPA entity for the {@code order_audit_log} table.
 *
 * <p>Schema owned by Flyway (V7__create_order_audit_log.sql). Hibernate runs in
 * {@code ddl-auto=validate} — all fields must match DB columns exactly.
 *
 * <p>This table is an <strong>append-only ledger</strong>. Rows are never updated
 * or deleted. Every row is written by {@link com.example.wms.order.statemachine.OrderStateMachine}
 * in the same transaction as the status change it records.
 *
 * <p>{@code previousStatus} is {@code null} only for the very first entry when an
 * order is created (the {@code null → CREATED} initialisation entry). All subsequent
 * entries have a non-null {@code previousStatus}.
 *
 * <p>{@code changedBy} stores the actor's username directly — intentionally not a FK,
 * so audit history survives user deactivation or deletion.
 */
@Entity
@Table(name = "order_audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The order this entry belongs to.
     * Lazy-loaded; callers reading the audit trail should fetch via the repository.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_audit_order"))
    private Order order;

    /**
     * Status before the transition. {@code null} only for the initial creation entry
     * ({@code null → CREATED}). All subsequent entries carry a non-null value.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private OrderStatus previousStatus;

    /** Status the order moved into. Never null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private OrderStatus newStatus;

    /**
     * Username of the actor who triggered the transition — either the client or a
     * warehouse manager. Not a FK so history survives user account changes.
     */
    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    /** Wall-clock time of the transition. Set to UTC now on persist; never updated. */
    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    @PrePersist
    void prePersist() {
        this.changedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

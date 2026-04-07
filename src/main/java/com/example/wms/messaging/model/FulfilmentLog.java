package com.example.wms.messaging.model;

import com.example.wms.messaging.dto.FulfillmentEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * JPA entity for the {@code fulfilment_log} table.
 *
 * <p>Schema is owned by Flyway (V10__create_fulfilment_log.sql). Hibernate runs
 * in {@code ddl-auto=validate} — every field must match DB columns exactly.
 *
 * <h2>Idempotency</h2>
 * {@code order_id} is the natural primary key (no surrogate). The database PK
 * constraint is the ultimate idempotency guard: attempting to insert a second
 * row for the same {@code order_id} throws a {@code DataIntegrityViolationException}.
 * The consumer performs an {@code existsById} pre-check to detect duplicates
 * cleanly before attempting the insert — the PK constraint is a belt-and-suspenders
 * safety net.
 *
 * <h2>Immutability</h2>
 * This is an append-only log table — no updates are ever issued. There is no
 * {@code @PreUpdate} and no mutable fields after construction.
 *
 * <h2>No FK to users</h2>
 * {@code client} is stored as a plain VARCHAR snapshot, not a FK to {@code users}.
 * This allows the SLA report query to read client names without a join, and
 * ensures the record survives if the user is later deactivated.
 */
@Entity
@Table(name = "fulfilment_log")
@Getter
@Setter
@NoArgsConstructor
public class FulfilmentLog {

    /**
     * The order UUID — natural PK and idempotency key.
     * There is no {@code @GeneratedValue}: the value is supplied by the
     * {@link FulfillmentEvent} and must match an existing row in {@code orders}.
     */
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    /**
     * Snapshot of the client username at fulfilment time.
     * Stored directly to avoid joins in the SLA reporting query.
     */
    @Column(name = "client", nullable = false, updatable = false, length = 100)
    private String client;

    /**
     * Number of distinct line items in the order at the time of fulfilment.
     * Must be > 0 (DB CHECK constraint enforces this).
     */
    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    /**
     * UTC timestamp when the Kafka consumer processed the fulfillment event.
     * Used by the SLA report to list the 5 most recently fulfilled orders.
     */
    @Column(name = "fulfilled_at", nullable = false, updatable = false)
    private OffsetDateTime fulfilledAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Builds a {@code FulfilmentLog} entry from a fully populated
     * {@link FulfillmentEvent}.
     *
     * <p>{@code fulfilledAt} is set to UTC now — the consumer-side processing
     * timestamp, not the delivery date on the event. This distinction matters
     * for the SLA report which measures end-to-end pipeline latency.
     *
     * @param event the inbound Kafka event; must have a non-null {@code orderId},
     *              non-blank {@code clientUsername}, and non-empty {@code items}
     * @return a new, unpersisted entity ready for {@code saveAndFlush()}
     */
    public static FulfilmentLog of(FulfillmentEvent event) {
        FulfilmentLog entry = new FulfilmentLog();
        entry.orderId     = event.getOrderId();
        entry.client      = event.getClientUsername();
        entry.itemCount   = event.getItems().size();
        entry.fulfilledAt = OffsetDateTime.now(ZoneOffset.UTC);
        return entry;
    }
}

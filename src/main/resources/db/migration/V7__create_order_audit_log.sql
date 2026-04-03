-- V7__create_order_audit_log.sql
-- Immutable append-only audit trail of every order status transition.
-- Written in the SAME transaction as the status change (OrderStateMachine).
-- Rows are never updated or deleted — treat this table as a ledger.

CREATE TABLE order_audit_log (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id        UUID          NOT NULL,
    previous_status VARCHAR(20),                           -- nullable: NULL on the very first transition (→ CREATED)
    new_status      VARCHAR(20)   NOT NULL,
    changed_by      VARCHAR(100)  NOT NULL,                -- username of the actor (client or manager)
    changed_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_audit_log PRIMARY KEY (id),

    CONSTRAINT chk_audit_previous_status CHECK (previous_status IS NULL OR previous_status IN (
        'CREATED', 'AWAITING_APPROVAL', 'APPROVED',
        'DECLINED', 'UNDER_DELIVERY', 'FULFILLED', 'CANCELED'
    )),

    CONSTRAINT chk_audit_new_status CHECK (new_status IN (
        'CREATED', 'AWAITING_APPROVAL', 'APPROVED',
        'DECLINED', 'UNDER_DELIVERY', 'FULFILLED', 'CANCELED'
    )),

    CONSTRAINT chk_audit_statuses_differ
        CHECK (previous_status IS NULL OR previous_status <> new_status),

    CONSTRAINT fk_audit_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT            -- audit history must never be silently removed
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Primary access pattern: fetch full audit trail for a given order.
-- Secondary: SLA report queries filter on new_status (AWAITING_APPROVAL, FULFILLED).
CREATE INDEX idx_audit_log_order_id   ON order_audit_log (order_id);
CREATE INDEX idx_audit_log_new_status ON order_audit_log (new_status);

COMMENT ON TABLE  order_audit_log              IS 'Append-only log of every order state transition; never updated or deleted';
COMMENT ON COLUMN order_audit_log.previous_status IS 'Status before the transition; NULL only for the initial CREATED entry';
COMMENT ON COLUMN order_audit_log.changed_by   IS 'Username of the actor who triggered the transition (not a FK — username must survive user deactivation)';
COMMENT ON COLUMN order_audit_log.changed_at   IS 'Wall-clock time of the transition; used for SLA avg fulfilment time calculations';

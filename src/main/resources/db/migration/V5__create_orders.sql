-- V5__create_orders.sql
-- Core orders table. Status uses a VARCHAR + CHECK constraint rather than a
-- PostgreSQL ENUM type so that adding future statuses is a simple migration
-- (ALTER TABLE ... ADD CONSTRAINT replacement) instead of the more disruptive
-- ALTER TYPE ... ADD VALUE which cannot be rolled back in a transaction.

CREATE TABLE orders (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_number   VARCHAR(20)   NOT NULL,
    client_id      UUID          NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    decline_reason VARCHAR(1000),                          -- nullable; populated on DECLINED only
    submitted_at   TIMESTAMPTZ,                            -- nullable; set when client submits (→ AWAITING_APPROVAL)
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_orders              PRIMARY KEY (id),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),

    CONSTRAINT chk_orders_status CHECK (status IN (
        'CREATED',
        'AWAITING_APPROVAL',
        'APPROVED',
        'DECLINED',
        'UNDER_DELIVERY',
        'FULFILLED',
        'CANCELED'
    )),

    CONSTRAINT chk_orders_order_number_format
        CHECK (order_number ~ '^ORD-[0-9]{4}-[0-9]{5}$'),

    CONSTRAINT chk_orders_decline_reason_only_on_declined
        CHECK (decline_reason IS NULL OR status = 'DECLINED'),

    CONSTRAINT fk_orders_client
        FOREIGN KEY (client_id) REFERENCES users (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT        -- never silently remove orders when a user is deleted
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Support the two most frequent query patterns:
--   1. Client lists their own orders            → WHERE client_id = ?
--   2. Manager filters all orders by status     → WHERE status = ?
CREATE INDEX idx_orders_client_id ON orders (client_id);
CREATE INDEX idx_orders_status    ON orders (status);

COMMENT ON TABLE  orders               IS 'Customer purchase orders — drives the entire warehouse fulfilment lifecycle';
COMMENT ON COLUMN orders.order_number  IS 'Human-readable identifier generated on persist; format ORD-YYYY-NNNNN (zero-padded 5-digit sequence)';
COMMENT ON COLUMN orders.status        IS 'State-machine status; only OrderStateMachine.java may change this value';
COMMENT ON COLUMN orders.decline_reason IS 'Manager-supplied reason; present only when status = DECLINED';
COMMENT ON COLUMN orders.submitted_at  IS 'Timestamp when client submitted the order for approval; NULL while in CREATED state';

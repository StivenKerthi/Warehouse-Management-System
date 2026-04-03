-- V8__create_deliveries.sql
-- One delivery row per order. A delivery ties an order to a specific calendar
-- date and one or more trucks (see delivery_trucks join table in V9).
-- An order has at most one delivery record; enforced by the unique constraint.

CREATE TABLE deliveries (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id       UUID          NOT NULL,
    delivery_date  DATE          NOT NULL,                 -- weekday-only; validated in application layer
    scheduled_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    scheduled_by   VARCHAR(100)  NOT NULL,                 -- username of the manager who scheduled

    CONSTRAINT pk_deliveries          PRIMARY KEY (id),
    CONSTRAINT uq_deliveries_order_id UNIQUE (order_id),  -- one delivery record per order

    CONSTRAINT chk_deliveries_date_not_past
        CHECK (delivery_date >= '2000-01-01'),             -- sanity guard; real past-date prevention is in the application

    CONSTRAINT fk_deliveries_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT            -- do not cascade-delete delivery records if an order is somehow removed
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Cron job queries deliveries by delivery_date to find orders ready to fulfil.
-- The unique constraint on order_id already creates an implicit index for that column.
CREATE INDEX idx_deliveries_delivery_date ON deliveries (delivery_date);

COMMENT ON TABLE  deliveries              IS 'Scheduled delivery for an approved order; one row per order maximum';
COMMENT ON COLUMN deliveries.delivery_date IS 'Target delivery date; must be a weekday within the configured window; validated by EligibleDayCalculator';
COMMENT ON COLUMN deliveries.scheduled_by IS 'Username of the WAREHOUSE_MANAGER who scheduled the delivery; stored as VARCHAR (not FK) to survive user deactivation';
COMMENT ON COLUMN deliveries.scheduled_at IS 'Timestamp when the delivery was scheduled; used for auditing';

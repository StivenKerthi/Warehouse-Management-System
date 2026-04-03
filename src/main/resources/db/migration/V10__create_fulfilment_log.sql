-- V10__create_fulfilment_log.sql
-- Idempotent record of every fulfilled order, written by the Kafka consumer.
-- order_id is the PRIMARY KEY — inserting a duplicate order_id throws a
-- PK violation which the consumer catches, logs, and ACKs without re-processing.
-- This is the DB-level idempotency guard for the Kafka at-least-once pipeline.

CREATE TABLE fulfilment_log (
    order_id      UUID          NOT NULL,
    client        VARCHAR(100)  NOT NULL,                  -- username of the order's client at fulfilment time
    item_count    INTEGER       NOT NULL,
    fulfilled_at  TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_fulfilment_log PRIMARY KEY (order_id),  -- PK doubles as the idempotency key

    CONSTRAINT chk_fulfilment_item_count_gt_0
        CHECK (item_count > 0),

    CONSTRAINT fk_fulfilment_log_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT            -- fulfilment records must never be silently removed
);

-- No additional index needed — all queries hit by PK (order_id).
-- SLA report queries for recent fulfilments use fulfilled_at; add index if profiling shows need.
CREATE INDEX idx_fulfilment_log_fulfilled_at ON fulfilment_log (fulfilled_at DESC);

COMMENT ON TABLE  fulfilment_log             IS 'Idempotent fulfilment record written by the Kafka consumer; order_id PK prevents duplicate processing';
COMMENT ON COLUMN fulfilment_log.order_id    IS 'PK and idempotency key — a second INSERT for the same order_id is caught as a PK violation and silently ACKed';
COMMENT ON COLUMN fulfilment_log.client      IS 'Snapshot of the client username at fulfilment time; stored directly to avoid joins in SLA queries';
COMMENT ON COLUMN fulfilment_log.item_count  IS 'Total number of distinct line items in the order at the time of fulfilment';
COMMENT ON COLUMN fulfilment_log.fulfilled_at IS 'Timestamp when the cron job transitioned the order to FULFILLED; used for SLA recent-fulfilments query';

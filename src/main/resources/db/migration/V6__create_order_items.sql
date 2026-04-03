-- V6__create_order_items.sql
-- Line items belonging to an order. unit_price_snapshot locks the price at
-- the moment the order is created so that subsequent inventory price changes
-- do not affect existing orders.

CREATE TABLE order_items (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    order_id             UUID           NOT NULL,
    inventory_item_id    UUID           NOT NULL,
    requested_quantity   INTEGER        NOT NULL,
    unit_price_snapshot  NUMERIC(12, 2) NOT NULL,          -- price locked at order creation time
    deadline_date        DATE,                              -- nullable soft-warning; does NOT hard-block scheduling

    CONSTRAINT pk_order_items PRIMARY KEY (id),

    CONSTRAINT chk_order_items_quantity_gt_0
        CHECK (requested_quantity > 0),

    CONSTRAINT chk_order_items_price_snapshot_gte_0
        CHECK (unit_price_snapshot >= 0),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,            -- items are not meaningful without their parent order

    CONSTRAINT fk_order_items_inventory_item
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT            -- block deletion of inventory items that are referenced by any order
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Loading all items for a given order is the dominant access pattern.
-- The FK on inventory_item_id also benefits from an index for the RESTRICT check.
CREATE INDEX idx_order_items_order_id          ON order_items (order_id);
CREATE INDEX idx_order_items_inventory_item_id ON order_items (inventory_item_id);

COMMENT ON TABLE  order_items                       IS 'Line items of an order; one row per inventory item requested';
COMMENT ON COLUMN order_items.unit_price_snapshot   IS 'Price per unit copied from inventory_items.unit_price at order creation; never updated retroactively';
COMMENT ON COLUMN order_items.requested_quantity    IS 'Number of units requested; must be > 0; inventory is decremented at delivery scheduling, not approval';
COMMENT ON COLUMN order_items.deadline_date         IS 'Optional client-requested delivery deadline; treated as a soft warning — manager is flagged but scheduling is not blocked';

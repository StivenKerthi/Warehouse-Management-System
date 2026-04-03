-- V3__create_inventory_items.sql
-- Warehouse stock catalogue. Quantity may never go below zero; the application
-- layer also guards this, but the DB constraint is the final safety net.

CREATE TABLE inventory_items (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    name           VARCHAR(255)   NOT NULL,
    quantity       INTEGER        NOT NULL DEFAULT 0,
    unit_price     NUMERIC(12, 2),                        -- nullable: price may be unknown at creation
    package_volume NUMERIC(10, 4),                        -- m³ per single unit; nullable initially
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_inventory_items         PRIMARY KEY (id),
    CONSTRAINT uq_inventory_items_name    UNIQUE (name),
    CONSTRAINT chk_inventory_quantity_gte_0
        CHECK (quantity >= 0),
    CONSTRAINT chk_inventory_unit_price_positive
        CHECK (unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT chk_inventory_package_volume_positive
        CHECK (package_volume IS NULL OR package_volume > 0)
);

COMMENT ON TABLE  inventory_items                IS 'Warehouse stock catalogue — every storable item type';
COMMENT ON COLUMN inventory_items.quantity       IS 'Current on-hand stock level; decremented only when delivery is scheduled (APPROVED→UNDER_DELIVERY)';
COMMENT ON COLUMN inventory_items.unit_price     IS 'Current list price in the system currency; snapshots are taken per order item at creation time';
COMMENT ON COLUMN inventory_items.package_volume IS 'Volume occupied by a single unit in cubic metres (m³); used for truck capacity validation';

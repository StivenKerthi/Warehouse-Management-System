-- V9__create_delivery_trucks.sql
-- Many-to-many join table linking a delivery to the trucks assigned to it.
-- Multiple trucks may be assigned to one delivery when the order volume exceeds
-- any single truck's capacity. Each truck can only appear once per delivery
-- (enforced by the composite PK).
--
-- One-delivery-per-truck-per-day is a scheduling constraint enforced in the
-- application layer (DeliveryService) and not duplicated here because it
-- requires joining with deliveries.delivery_date — not expressible as a simple
-- table-level constraint.

CREATE TABLE delivery_trucks (
    delivery_id  UUID  NOT NULL,
    truck_id     UUID  NOT NULL,

    CONSTRAINT pk_delivery_trucks
        PRIMARY KEY (delivery_id, truck_id),

    CONSTRAINT fk_delivery_trucks_delivery
        FOREIGN KEY (delivery_id) REFERENCES deliveries (id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,            -- removing a delivery removes its truck assignments

    CONSTRAINT fk_delivery_trucks_truck
        FOREIGN KEY (truck_id) REFERENCES trucks (id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT            -- block truck deletion if it has delivery history
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Composite PK covers (delivery_id, truck_id) lookups.
-- Reverse index supports: "which deliveries is truck X assigned to?"
-- Used to enforce the one-delivery-per-truck-per-day rule in DeliveryService.
CREATE INDEX idx_delivery_trucks_truck_id ON delivery_trucks (truck_id);

COMMENT ON TABLE  delivery_trucks             IS 'Join table: trucks assigned to a delivery; composite PK prevents duplicate assignment';
COMMENT ON COLUMN delivery_trucks.delivery_id IS 'FK to deliveries; cascades on delete so orphan rows cannot exist';
COMMENT ON COLUMN delivery_trucks.truck_id    IS 'FK to trucks; RESTRICT prevents deleting a truck with delivery history';

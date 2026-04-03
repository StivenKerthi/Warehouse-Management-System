-- V4__create_trucks.sql
-- Fleet of delivery trucks. A truck can only be assigned to one delivery per day;
-- that uniqueness is enforced at the application/delivery layer (TASK-036).
-- Here we enforce identity uniqueness and positive volume.

CREATE TABLE trucks (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    chassis_number   VARCHAR(50)   NOT NULL,
    license_plate    VARCHAR(20)   NOT NULL,
    container_volume NUMERIC(10, 4) NOT NULL,             -- usable cargo space in m³
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_trucks                      PRIMARY KEY (id),
    CONSTRAINT uq_trucks_chassis_number       UNIQUE (chassis_number),
    CONSTRAINT uq_trucks_license_plate        UNIQUE (license_plate),
    CONSTRAINT chk_trucks_container_volume_gt_0
        CHECK (container_volume > 0)
);

COMMENT ON TABLE  trucks                   IS 'Delivery fleet; inactive trucks are excluded from scheduling';
COMMENT ON COLUMN trucks.chassis_number    IS 'Vehicle Identification Number (VIN) or equivalent chassis identifier — globally unique';
COMMENT ON COLUMN trucks.license_plate     IS 'Road-legal registration plate — unique within the fleet';
COMMENT ON COLUMN trucks.container_volume  IS 'Total usable cargo space in cubic metres (m³); must match or exceed order total volume to be scheduled';
COMMENT ON COLUMN trucks.active            IS 'Soft-delete flag; deactivated trucks are not offered for new deliveries but history is preserved';

-- V2__create_system_config.sql
-- Creates the system_config key-value store and seeds the mandatory
-- delivery_window_days default value of 14 days.

CREATE TABLE system_config (
    config_key   VARCHAR(100)  NOT NULL,
    config_value VARCHAR(500)  NOT NULL,
    updated_by   VARCHAR(100),                          -- nullable: NULL on seed rows
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_system_config PRIMARY KEY (config_key)
);

COMMENT ON TABLE  system_config              IS 'Runtime-configurable key-value pairs managed by SYSTEM_ADMIN';
COMMENT ON COLUMN system_config.config_key   IS 'Immutable application-level key (e.g. delivery_window_days)';
COMMENT ON COLUMN system_config.config_value IS 'String-encoded value; application layer is responsible for type coercion';
COMMENT ON COLUMN system_config.updated_by   IS 'Username of the admin who last changed this row; NULL for seed data';

-- ── Seed data ────────────────────────────────────────────────────────────────
-- delivery_window_days: maximum number of calendar days ahead the manager can
-- schedule a delivery.  Hard ceiling is enforced in code at 30 days.
INSERT INTO system_config (config_key, config_value, updated_by, updated_at)
VALUES ('delivery_window_days', '14', NULL, NOW());

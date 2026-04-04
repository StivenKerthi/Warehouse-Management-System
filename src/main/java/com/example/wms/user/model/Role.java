package com.example.wms.user.model;

/**
 * Application roles, mirroring the {@code user_role} PostgreSQL enum.
 * Values must stay in sync with the DB enum — Flyway owns the DDL.
 */
public enum Role {
    CLIENT,
    WAREHOUSE_MANAGER,
    SYSTEM_ADMIN
}

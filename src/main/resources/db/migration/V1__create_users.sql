-- V1__create_users.sql
-- Creates the user_role enum type and the users table.
-- Flyway owns all DDL — Hibernate is set to ddl-auto=validate only.

CREATE TYPE user_role AS ENUM ('CLIENT', 'WAREHOUSE_MANAGER', 'SYSTEM_ADMIN');

CREATE TABLE users (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    username      VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    role          user_role     NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users          PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

COMMENT ON TABLE  users             IS 'Application users — CLIENT, WAREHOUSE_MANAGER, SYSTEM_ADMIN';
COMMENT ON COLUMN users.password_hash IS 'BCrypt-hashed password; plain-text is never stored';
COMMENT ON COLUMN users.active      IS 'Soft-delete flag; inactive users cannot log in';

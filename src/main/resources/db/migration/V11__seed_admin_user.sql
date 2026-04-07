-- V11__seed_admin_user.sql
-- Inserts a default SYSTEM_ADMIN user for local development and demo purposes.
-- Credentials: username = sysadmin / password = password
-- ON CONFLICT DO NOTHING makes this migration safe to re-run.

INSERT INTO users (id, username, email, password_hash, role, active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'sysadmin',
    'sysadmin@wms.com',
    '$2a$12$Oxo/ShZ5fh90tV41eUSrLeyLmeH9rLGks6DxIyVaiWPnOrCBiHsXa',
    'SYSTEM_ADMIN',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (username) DO NOTHING;

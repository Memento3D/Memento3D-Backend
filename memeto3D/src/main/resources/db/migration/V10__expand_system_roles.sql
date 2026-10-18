-- Migration: V10__expand_system_roles.sql
-- Description: Expand users_role_check constraint to support enterprise multi-actor roles

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (
    role IN (
        'ROLE_USER',
        'ROLE_ADMIN',
        'ROLE_MANAGER',
        'ROLE_WAREHOUSE',
        'ROLE_OPERATOR',
        'ROLE_PROVIDER',
        'ROLE_DRIVER'
    )
);

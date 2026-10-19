-- ============================================================================
-- Migration: V18__account_profile_security_and_addresses.sql
-- Description: Account & Profile management.
--   1. users.date_of_birth       -> optional profile field
--   2. users.password_changed_at -> JWT revocation marker: any access token issued
--                                   before this instant is rejected (set on password
--                                   reset / change so old sessions die immediately).
--   3. user_addresses            -> per-user address book (recipient + shipping info).
--                                   Orders keep their own shipping_address text snapshot,
--                                   so editing/deleting an address never rewrites history.
-- Non-destructive: only ADD COLUMN / CREATE TABLE IF NOT EXISTS.
-- ============================================================================

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS public.user_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    province VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    label VARCHAR(20) NOT NULL DEFAULT 'HOME',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_addresses_label CHECK (label IN ('HOME', 'OFFICE', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_user_addresses_user_id ON public.user_addresses (user_id);

-- DB-level guarantee: at most ONE default address per user (partial unique index).
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_addresses_one_default_per_user
    ON public.user_addresses (user_id) WHERE is_default = TRUE;

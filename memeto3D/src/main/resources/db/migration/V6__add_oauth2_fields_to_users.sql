-- ============================================================================
-- Migration: V6__add_oauth2_fields_to_users.sql
-- Description: Add OAuth2 provider, provider_id, avatar_url, and make password nullable
-- ============================================================================

-- Add OAuth2 fields to users table
ALTER TABLE public.users 
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20) DEFAULT 'LOCAL' NOT NULL,
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);

-- Make password nullable for OAuth2 users who don't authenticate with password
ALTER TABLE public.users 
    ALTER COLUMN password DROP NOT NULL;

-- Create index for quick lookup by provider and provider_id
CREATE INDEX IF NOT EXISTS idx_users_provider_provider_id 
    ON public.users (provider, provider_id);

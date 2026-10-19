-- V15: Align notifications and physical_quality_checks schema with JPA entities

-- 1. Notifications Table Alignment
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS type VARCHAR(50);
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS reference_id UUID;
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS reference_type VARCHAR(50);

-- Migrate data from event_type to type if event_type exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'event_type'
    ) THEN
        UPDATE public.notifications SET type = event_type WHERE type IS NULL;
    END IF;
END $$;

-- Fallback default for any records where type is still null
UPDATE public.notifications SET type = 'GENERAL' WHERE type IS NULL;

-- Enforce NOT NULL on type
ALTER TABLE public.notifications ALTER COLUMN type SET NOT NULL;

-- Additional indexes for Notification queries
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON public.notifications (user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON public.notifications (created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_ref ON public.notifications (reference_id, reference_type);

-- 2. Physical Quality Checks Table Alignment
ALTER TABLE public.physical_quality_checks ADD COLUMN IF NOT EXISTS order_item_id UUID;
ALTER TABLE public.physical_quality_checks ADD COLUMN IF NOT EXISTS reprint_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_physical_quality_checks_order_item ON public.physical_quality_checks (order_item_id);

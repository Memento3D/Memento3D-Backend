-- =========================================================================
-- V23: Order Email Notifications & Deduplication Log
-- =========================================================================

CREATE TABLE IF NOT EXISTS public.order_email_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES public.print_orders(id) ON DELETE CASCADE,
    email_type VARCHAR(50) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    recipient_name VARCHAR(255),
    order_code VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_email_type UNIQUE (order_id, email_type)
);

CREATE INDEX IF NOT EXISTS idx_order_email_notif_status_retry
    ON public.order_email_notifications (status, retry_count);

CREATE INDEX IF NOT EXISTS idx_order_email_notif_order
    ON public.order_email_notifications (order_id);

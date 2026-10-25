-- ============================================================================
-- Migration: V19__manual_review_queue_and_integrity_constraints.sql
-- Description:
--   1. Create manual_review_items table for Human-in-the-Loop exception handling
--      (KYC, Photogrammetry Reconstruction QC, Payment Fraud, Refunds, Print QC, Payouts)
--   2. Add check constraints for financial and business data integrity
-- ============================================================================

-- 1. Manual Review Queue for Human-in-the-Loop Exception Management
CREATE TABLE IF NOT EXISTS public.manual_review_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_type VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reference_id UUID NOT NULL,
    reference_code VARCHAR(100),
    user_id UUID REFERENCES public.users(id) ON DELETE SET NULL,
    confidence_score NUMERIC(5, 2),
    reason_code VARCHAR(60) NOT NULL,
    reason_details TEXT,
    suggested_action VARCHAR(60),
    assigned_to UUID REFERENCES public.users(id) ON DELETE SET NULL,
    reviewer_notes TEXT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_manual_review_type CHECK (
        review_type IN ('KYC', 'RECONSTRUCTION', 'PAYMENT', 'REFUND', 'PRINT_QC', 'PAYOUT', 'FRAUD', 'DISPUTE')
    ),
    CONSTRAINT chk_manual_review_priority CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')
    ),
    CONSTRAINT chk_manual_review_status CHECK (
        status IN ('PENDING', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'ESCALATED')
    )
);

CREATE INDEX IF NOT EXISTS idx_manual_review_status_type ON public.manual_review_items (status, review_type);
CREATE INDEX IF NOT EXISTS idx_manual_review_reference_id ON public.manual_review_items (reference_id);
CREATE INDEX IF NOT EXISTS idx_manual_review_created_at ON public.manual_review_items (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_manual_review_priority ON public.manual_review_items (priority);

-- 2. Financial & Business Data Integrity Check Constraints
DO $$
BEGIN
    -- Ensure print_orders total_price is non-negative
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_print_orders_total_price_non_negative'
    ) THEN
        ALTER TABLE public.print_orders
            ADD CONSTRAINT chk_print_orders_total_price_non_negative CHECK (total_price >= 0);
    END IF;

    -- Ensure payment_transactions amount is positive
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_payment_transactions_amount_positive'
    ) THEN
        ALTER TABLE public.payment_transactions
            ADD CONSTRAINT chk_payment_transactions_amount_positive CHECK (amount > 0);
    END IF;

    -- Ensure order_items quantity is positive
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'order_items') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'chk_order_items_quantity_positive'
        ) THEN
            ALTER TABLE public.order_items
                ADD CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0);
        END IF;
    END IF;
END $$;

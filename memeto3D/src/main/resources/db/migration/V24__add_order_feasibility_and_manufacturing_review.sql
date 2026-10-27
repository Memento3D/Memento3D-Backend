-- =========================================================================
-- V24: Order Feasibility Review & Workshop Manufacturing Inspection
-- =========================================================================
-- Enables workshop managers and technicians to inspect custom 3D model orders,
-- issue final quotes, request revisions, and track customer approval before payment.

ALTER TABLE public.print_orders
    ADD COLUMN IF NOT EXISTS feasibility_status VARCHAR(50) DEFAULT 'PENDING_REVIEW',
    ADD COLUMN IF NOT EXISTS feasibility_notes TEXT,
    ADD COLUMN IF NOT EXISTS estimated_production_days INT,
    ADD COLUMN IF NOT EXISTS revision_glb_url TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS customer_accepted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_print_orders_feasibility_status
    ON public.print_orders (feasibility_status);

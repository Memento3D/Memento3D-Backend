-- =========================================================================
-- V22: Order Management Redesign, Order Snapshots & Outsourced Manufacturing
-- =========================================================================

-- 1. Extend print_orders with shipping logistics, outsourced manufacturing & fee breakdowns
ALTER TABLE public.print_orders
    ADD COLUMN IF NOT EXISTS shipping_carrier VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tracking_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS shipping_status VARCHAR(50) DEFAULT 'PREPARING',
    ADD COLUMN IF NOT EXISTS internal_partner_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS internal_cost NUMERIC(12, 2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS internal_qc_notes TEXT,
    ADD COLUMN IF NOT EXISTS customization_fee NUMERIC(12, 2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS production_surcharge NUMERIC(12, 2) DEFAULT 0;

-- 2. Extend order_items with comprehensive product snapshot columns (preserves historical order accuracy)
ALTER TABLE public.order_items
    ADD COLUMN IF NOT EXISTS sku_snapshot VARCHAR(100),
    ADD COLUMN IF NOT EXISTS category_name_snapshot VARCHAR(100),
    ADD COLUMN IF NOT EXISTS material_snapshot VARCHAR(100),
    ADD COLUMN IF NOT EXISTS dimensions_snapshot VARCHAR(100),
    ADD COLUMN IF NOT EXISTS technology_snapshot VARCHAR(50),
    ADD COLUMN IF NOT EXISTS color_snapshot VARCHAR(50),
    ADD COLUMN IF NOT EXISTS finish_snapshot VARCHAR(100),
    ADD COLUMN IF NOT EXISTS base_snapshot VARCHAR(100);

-- 3. Composite indexes for high-performance order search & filtering
CREATE INDEX IF NOT EXISTS idx_print_orders_user_status_created
    ON public.print_orders (user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_print_orders_status_created
    ON public.print_orders (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_print_orders_tracking_code
    ON public.print_orders (tracking_code)
    WHERE tracking_code IS NOT NULL;

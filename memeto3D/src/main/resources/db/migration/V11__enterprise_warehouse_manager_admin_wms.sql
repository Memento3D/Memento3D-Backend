-- V11: Enterprise WMS, Multi-step Receipts, Issues, Transfers, Adjustments, Stock Counts, Audit Logs, and Admin Controls

-- 1. Upgrade warehouses table
ALTER TABLE public.warehouses DROP CONSTRAINT IF EXISTS chk_warehouses_status;
ALTER TABLE public.warehouses ADD CONSTRAINT chk_warehouses_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE'));

ALTER TABLE public.warehouses ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE public.warehouses ADD COLUMN IF NOT EXISTS address VARCHAR(500);
ALTER TABLE public.warehouses ADD COLUMN IF NOT EXISTS manager_id UUID REFERENCES public.users(id) ON DELETE SET NULL;
ALTER TABLE public.warehouses ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES public.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_warehouses_manager ON public.warehouses (manager_id);

-- 2. Upgrade materials catalog table
ALTER TABLE public.materials ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE public.materials ADD COLUMN IF NOT EXISTS unit VARCHAR(50) NOT NULL DEFAULT 'g';
ALTER TABLE public.materials ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE public.materials ADD COLUMN IF NOT EXISTS minimum_stock NUMERIC(14, 2) NOT NULL DEFAULT 500;
ALTER TABLE public.materials ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES public.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_materials_category ON public.materials (category);
CREATE INDEX IF NOT EXISTS idx_materials_brand ON public.materials (brand);

-- 3. Upgrade inventory_balances table
ALTER TABLE public.inventory_balances ADD COLUMN IF NOT EXISTS minimum_stock NUMERIC(14, 2) NOT NULL DEFAULT 500;

-- 4. Upgrade inventory_transactions (Ledger) to support enterprise transaction types
ALTER TABLE public.inventory_transactions DROP CONSTRAINT IF EXISTS chk_transactions_type;
ALTER TABLE public.inventory_transactions ADD CONSTRAINT chk_transactions_type CHECK (transaction_type IN (
    'STOCK_IN', 'STOCK_OUT', 'RESERVE', 'RELEASE', 'ADJUST_INCREASE', 'ADJUST_DECREASE',
    'TRANSFER_IN', 'TRANSFER_OUT', 'RETURN_IN', 'DAMAGED', 'WASTE',
    'RECEIPT', 'ISSUE', 'RESERVATION_HOLD', 'RESERVATION_RELEASE', 'PRINT_ISSUE', 'PRINT_RETURN'
));

ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES public.users(id) ON DELETE SET NULL;
ALTER TABLE public.inventory_transactions ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(14, 2) DEFAULT 0;

-- 5. Enterprise Goods Receipts (Multi-step with items)
CREATE TABLE IF NOT EXISTS public.goods_receipts (
    id UUID PRIMARY KEY,
    receipt_code VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES public.warehouses(id) ON DELETE RESTRICT,
    supplier VARCHAR(200),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    created_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    approved_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_goods_receipts_code UNIQUE (receipt_code),
    CONSTRAINT chk_goods_receipts_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'COMPLETED', 'CANCELLED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_goods_receipts_wh ON public.goods_receipts (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_goods_receipts_status ON public.goods_receipts (status);
CREATE INDEX IF NOT EXISTS idx_goods_receipts_created ON public.goods_receipts (created_at DESC);

CREATE TABLE IF NOT EXISTS public.goods_receipt_items (
    id UUID PRIMARY KEY,
    receipt_id UUID NOT NULL REFERENCES public.goods_receipts(id) ON DELETE CASCADE,
    material_id UUID NOT NULL REFERENCES public.materials(id) ON DELETE RESTRICT,
    quantity NUMERIC(14, 2) NOT NULL,
    unit_cost NUMERIC(14, 2) NOT NULL DEFAULT 0,
    batch_number VARCHAR(100),
    note TEXT,

    CONSTRAINT chk_receipt_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_receipt_item_cost CHECK (unit_cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_goods_receipt_items_receipt ON public.goods_receipt_items (receipt_id);
CREATE INDEX IF NOT EXISTS idx_goods_receipt_items_material ON public.goods_receipt_items (material_id);

-- 6. Enterprise Stock Issues (Multi-step with items)
CREATE TABLE IF NOT EXISTS public.enterprise_stock_issues (
    id UUID PRIMARY KEY,
    issue_code VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES public.warehouses(id) ON DELETE RESTRICT,
    reference_type VARCHAR(100),
    reference_id VARCHAR(100),
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    created_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    approved_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_enterprise_issues_code UNIQUE (issue_code),
    CONSTRAINT chk_enterprise_issues_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'COMPLETED', 'CANCELLED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_enterprise_issues_wh ON public.enterprise_stock_issues (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_issues_status ON public.enterprise_stock_issues (status);

CREATE TABLE IF NOT EXISTS public.enterprise_stock_issue_items (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES public.enterprise_stock_issues(id) ON DELETE CASCADE,
    material_id UUID NOT NULL REFERENCES public.materials(id) ON DELETE RESTRICT,
    requested_quantity NUMERIC(14, 2) NOT NULL,
    issued_quantity NUMERIC(14, 2) NOT NULL DEFAULT 0,
    note TEXT,

    CONSTRAINT chk_issue_item_req_qty CHECK (requested_quantity > 0),
    CONSTRAINT chk_issue_item_iss_qty CHECK (issued_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_issue_items_issue ON public.enterprise_stock_issue_items (issue_id);
CREATE INDEX IF NOT EXISTS idx_issue_items_material ON public.enterprise_stock_issue_items (material_id);

-- 7. Warehouse Transfers (Transfer between facilities)
CREATE TABLE IF NOT EXISTS public.warehouse_transfers (
    id UUID PRIMARY KEY,
    transfer_code VARCHAR(100) NOT NULL,
    source_warehouse_id UUID NOT NULL REFERENCES public.warehouses(id) ON DELETE RESTRICT,
    destination_warehouse_id UUID NOT NULL REFERENCES public.warehouses(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    created_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    approved_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    shipped_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_warehouse_transfers_code UNIQUE (transfer_code),
    CONSTRAINT chk_transfers_different_wh CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT chk_transfers_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_transfers_src_wh ON public.warehouse_transfers (source_warehouse_id);
CREATE INDEX IF NOT EXISTS idx_transfers_dst_wh ON public.warehouse_transfers (destination_warehouse_id);
CREATE INDEX IF NOT EXISTS idx_transfers_status ON public.warehouse_transfers (status);

CREATE TABLE IF NOT EXISTS public.warehouse_transfer_items (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES public.warehouse_transfers(id) ON DELETE CASCADE,
    material_id UUID NOT NULL REFERENCES public.materials(id) ON DELETE RESTRICT,
    quantity NUMERIC(14, 2) NOT NULL,
    note TEXT,

    CONSTRAINT chk_transfer_item_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_transfer_items_transfer ON public.warehouse_transfer_items (transfer_id);

-- 8. Stock Adjustments (Physical count variance or discrepancy corrections)
CREATE TABLE IF NOT EXISTS public.stock_adjustments (
    id UUID PRIMARY KEY,
    adjustment_code VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES public.warehouses(id) ON DELETE RESTRICT,
    material_id UUID NOT NULL REFERENCES public.materials(id) ON DELETE RESTRICT,
    current_quantity NUMERIC(14, 2) NOT NULL,
    actual_quantity NUMERIC(14, 2) NOT NULL,
    difference NUMERIC(14, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    evidence TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    requested_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    approved_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    applied_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stock_adjustments_code UNIQUE (adjustment_code),
    CONSTRAINT chk_adjustments_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'APPLIED', 'CANCELLED')),
    CONSTRAINT chk_adjustments_actual_non_negative CHECK (actual_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_adjustments_wh ON public.stock_adjustments (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_adjustments_mat ON public.stock_adjustments (material_id);
CREATE INDEX IF NOT EXISTS idx_adjustments_status ON public.stock_adjustments (status);

-- 9. Stock Counts (Inventory Audit Sessions)
CREATE TABLE IF NOT EXISTS public.stock_counts (
    id UUID PRIMARY KEY,
    count_code VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES public.warehouses(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stock_counts_code UNIQUE (count_code),
    CONSTRAINT chk_stock_counts_status CHECK (status IN ('DRAFT', 'COUNTING', 'REVIEW', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_stock_counts_wh ON public.stock_counts (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_counts_status ON public.stock_counts (status);

CREATE TABLE IF NOT EXISTS public.stock_count_items (
    id UUID PRIMARY KEY,
    stock_count_id UUID NOT NULL REFERENCES public.stock_counts(id) ON DELETE CASCADE,
    material_id UUID NOT NULL REFERENCES public.materials(id) ON DELETE RESTRICT,
    system_quantity NUMERIC(14, 2) NOT NULL DEFAULT 0,
    counted_quantity NUMERIC(14, 2),
    variance NUMERIC(14, 2),
    note TEXT
);

CREATE INDEX IF NOT EXISTS idx_stock_count_items_count ON public.stock_count_items (stock_count_id);

-- 10. System Audit Logs
DROP TABLE IF EXISTS public.audit_logs CASCADE;
CREATE TABLE public.audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES public.users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    before_data TEXT,
    after_data TEXT,
    ip_address VARCHAR(100),
    user_agent VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON public.audit_logs (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON public.audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON public.audit_logs (created_at DESC);

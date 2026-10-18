-- V8: Material, Warehouse, Inventory Balances, Receipts, Issues, Print Jobs, Reservations, Consumptions and Ledger

-- 1. Materials Catalog
CREATE TABLE public.materials (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    color VARCHAR(50) NOT NULL,
    color_hex VARCHAR(10),
    density NUMERIC(6, 3),
    diameter NUMERIC(4, 2) DEFAULT 1.75,
    print_temp_min INT,
    print_temp_max INT,
    bed_temp_min INT,
    bed_temp_max INT,
    unit_cost NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_materials_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_materials_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT fk_materials_created_by FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_materials_sku_lower ON public.materials (LOWER(sku));
CREATE INDEX idx_materials_status ON public.materials (status);
CREATE INDEX idx_materials_type ON public.materials (material_type);

-- 2. Warehouses
CREATE TABLE public.warehouses (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    location VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_warehouses_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_warehouses_created_by FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_warehouses_code_lower ON public.warehouses (LOWER(code));
CREATE INDEX idx_warehouses_status ON public.warehouses (status);

-- 3. Inventory Balances
CREATE TABLE public.inventory_balances (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    on_hand_gram NUMERIC(14, 2) NOT NULL DEFAULT 0,
    reserved_gram NUMERIC(14, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_balances_on_hand_non_negative CHECK (on_hand_gram >= 0),
    CONSTRAINT chk_balances_reserved_non_negative CHECK (reserved_gram >= 0),
    CONSTRAINT chk_balances_sufficient_stock CHECK (on_hand_gram >= reserved_gram),
    CONSTRAINT uq_inventory_warehouse_material UNIQUE (warehouse_id, material_id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_material FOREIGN KEY (material_id) REFERENCES public.materials (id) ON DELETE RESTRICT
);

CREATE INDEX idx_inventory_warehouse ON public.inventory_balances (warehouse_id);
CREATE INDEX idx_inventory_material ON public.inventory_balances (material_id);

-- 4. Stock Receipts (Goods Receipt)
CREATE TABLE public.stock_receipts (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    quantity_gram NUMERIC(14, 2) NOT NULL,
    unit_cost NUMERIC(14, 2) NOT NULL DEFAULT 0,
    supplier VARCHAR(200),
    batch_number VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL,
    note TEXT,
    received_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stock_receipts_number UNIQUE (receipt_number),
    CONSTRAINT uq_stock_receipts_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_stock_receipts_qty CHECK (quantity_gram > 0),
    CONSTRAINT chk_stock_receipts_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT fk_receipts_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_material FOREIGN KEY (material_id) REFERENCES public.materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_received_by FOREIGN KEY (received_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE INDEX idx_stock_receipts_wh_mat ON public.stock_receipts (warehouse_id, material_id);

-- 5. Stock Issues (Manual Material Issue)
CREATE TABLE public.stock_issues (
    id UUID PRIMARY KEY,
    issue_number VARCHAR(100) NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    quantity_gram NUMERIC(14, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    note TEXT,
    issued_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stock_issues_number UNIQUE (issue_number),
    CONSTRAINT uq_stock_issues_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_stock_issues_qty CHECK (quantity_gram > 0),
    CONSTRAINT fk_issues_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_issues_material FOREIGN KEY (material_id) REFERENCES public.materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_issues_issued_by FOREIGN KEY (issued_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE INDEX idx_stock_issues_wh_mat ON public.stock_issues (warehouse_id, material_id);

-- 6. Print Jobs
CREATE TABLE public.print_jobs (
    id UUID PRIMARY KEY,
    job_number VARCHAR(100) NOT NULL,
    order_id UUID,
    printer_id UUID,
    material_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    estimated_gram NUMERIC(14, 2) NOT NULL,
    estimated_duration_seconds INT,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_print_jobs_number UNIQUE (job_number),
    CONSTRAINT chk_print_jobs_status CHECK (status IN ('CREATED', 'RESERVED', 'ISSUED', 'PRINTING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_print_jobs_estimated_qty CHECK (estimated_gram > 0),
    CONSTRAINT fk_print_jobs_order FOREIGN KEY (order_id) REFERENCES public.print_orders (id) ON DELETE SET NULL,
    CONSTRAINT fk_print_jobs_printer FOREIGN KEY (printer_id) REFERENCES public.printers (id) ON DELETE SET NULL,
    CONSTRAINT fk_print_jobs_material FOREIGN KEY (material_id) REFERENCES public.materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_print_jobs_created_by FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE INDEX idx_print_jobs_status ON public.print_jobs (status);
CREATE INDEX idx_print_jobs_printer ON public.print_jobs (printer_id);
CREATE INDEX idx_print_jobs_material ON public.print_jobs (material_id);

-- 7. Stock Reservations (Print Job Reservation)
CREATE TABLE public.stock_reservations (
    id UUID PRIMARY KEY,
    print_job_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    reserved_gram NUMERIC(14, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reservations_status CHECK (status IN ('ACTIVE', 'FULFILLED', 'RELEASED')),
    CONSTRAINT chk_reservations_qty CHECK (reserved_gram > 0),
    CONSTRAINT fk_reservations_print_job FOREIGN KEY (print_job_id) REFERENCES public.print_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservations_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_material FOREIGN KEY (material_id) REFERENCES public.materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_created_by FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE INDEX idx_reservations_job ON public.stock_reservations (print_job_id);
CREATE INDEX idx_reservations_status ON public.stock_reservations (status);

-- 8. Print Job Consumptions (Issue & Reconciliation)
CREATE TABLE public.print_job_consumptions (
    id UUID PRIMARY KEY,
    print_job_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    issued_gram NUMERIC(14, 2) NOT NULL,
    used_gram NUMERIC(14, 2) NOT NULL DEFAULT 0,
    waste_gram NUMERIC(14, 2) NOT NULL DEFAULT 0,
    returned_gram NUMERIC(14, 2) NOT NULL DEFAULT 0,
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    reconciled_at TIMESTAMP WITH TIME ZONE,
    reconciled_by UUID,
    note TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_consumptions_job UNIQUE (print_job_id),
    CONSTRAINT chk_consumptions_issued_qty CHECK (issued_gram >= 0),
    CONSTRAINT chk_consumptions_used_qty CHECK (used_gram >= 0),
    CONSTRAINT chk_consumptions_waste_qty CHECK (waste_gram >= 0),
    CONSTRAINT chk_consumptions_returned_qty CHECK (returned_gram >= 0),
    CONSTRAINT chk_consumptions_reconcile_formula CHECK (
        NOT reconciled OR (issued_gram = used_gram + waste_gram + returned_gram)
    ),
    CONSTRAINT fk_consumptions_job FOREIGN KEY (print_job_id) REFERENCES public.print_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_consumptions_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_consumptions_material FOREIGN KEY (material_id) REFERENCES public.materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_consumptions_reconciled_by FOREIGN KEY (reconciled_by) REFERENCES public.users (id) ON DELETE SET NULL
);

-- 9. Immutable Inventory Ledger Transactions
CREATE TABLE public.inventory_transactions (
    id UUID PRIMARY KEY,
    transaction_type VARCHAR(30) NOT NULL,
    warehouse_id UUID NOT NULL,
    material_id UUID NOT NULL,
    delta_on_hand_gram NUMERIC(14, 2) NOT NULL DEFAULT 0,
    delta_reserved_gram NUMERIC(14, 2) NOT NULL DEFAULT 0,
    before_on_hand_gram NUMERIC(14, 2) NOT NULL,
    after_on_hand_gram NUMERIC(14, 2) NOT NULL,
    before_reserved_gram NUMERIC(14, 2) NOT NULL,
    after_reserved_gram NUMERIC(14, 2) NOT NULL,
    print_job_id UUID,
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(100),
    performed_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_transactions_type CHECK (transaction_type IN (
        'RECEIPT', 'ISSUE', 'RESERVATION_HOLD', 'RESERVATION_RELEASE', 'PRINT_ISSUE', 'PRINT_RETURN'
    )),
    CONSTRAINT fk_transactions_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_material FOREIGN KEY (material_id) REFERENCES public.materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_job FOREIGN KEY (print_job_id) REFERENCES public.print_jobs (id) ON DELETE SET NULL,
    CONSTRAINT fk_transactions_performed_by FOREIGN KEY (performed_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE INDEX idx_transactions_wh_mat_created ON public.inventory_transactions (warehouse_id, material_id, created_at DESC);
CREATE INDEX idx_transactions_job ON public.inventory_transactions (print_job_id);
CREATE INDEX idx_transactions_type ON public.inventory_transactions (transaction_type);

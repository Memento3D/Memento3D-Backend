-- V13: Memento3D Comprehensive Freemium & Production Schema (BE-03 to BE-15)

-- 1. Extend reconstruction_jobs for Tiering, Outbox & Order Linkage
ALTER TABLE public.reconstruction_jobs ADD COLUMN IF NOT EXISTS tier VARCHAR(30) NOT NULL DEFAULT 'PREVIEW';
ALTER TABLE public.reconstruction_jobs ADD COLUMN IF NOT EXISTS order_id UUID REFERENCES public.print_orders(id) ON DELETE SET NULL;
ALTER TABLE public.reconstruction_jobs ADD COLUMN IF NOT EXISTS order_item_id UUID;

CREATE INDEX IF NOT EXISTS idx_reconstruction_jobs_tier_status ON public.reconstruction_jobs (tier, status);
CREATE INDEX IF NOT EXISTS idx_reconstruction_jobs_order ON public.reconstruction_jobs (order_id);

-- 2. Preview Quotas for Free Tier Users
CREATE TABLE IF NOT EXISTS public.preview_quotas (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    total_quota INT NOT NULL DEFAULT 3,
    used_quota INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_preview_quotas_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_preview_quotas_user ON public.preview_quotas (user_id);

-- 3. Pricing Rules Engine
CREATE TABLE IF NOT EXISTS public.pricing_rules (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    calculation_type VARCHAR(50) NOT NULL, -- FIXED_PRICE, PERCENTAGE, QUANTITY_BASED, MANUAL_QUOTE
    category_id UUID REFERENCES public.product_categories(id) ON DELETE SET NULL,
    product_id UUID REFERENCES public.products(id) ON DELETE SET NULL,
    condition_type VARCHAR(50), -- SIZE, COMPLEXITY, MATERIAL, FINISHING, PACKAGING
    condition_value VARCHAR(100),
    modifier_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pricing_rules_active ON public.pricing_rules (active);
CREATE INDEX IF NOT EXISTS idx_pricing_rules_product ON public.pricing_rules (product_id);

-- 4. Price Quotes & Line Items
CREATE TABLE IF NOT EXISTS public.price_quotes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    product_id UUID REFERENCES public.products(id) ON DELETE SET NULL,
    base_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    customization_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    shipping_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    configuration_snapshot TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_price_quotes_user ON public.price_quotes (user_id);
CREATE INDEX IF NOT EXISTS idx_price_quotes_validity ON public.price_quotes (valid_until, is_used);

CREATE TABLE IF NOT EXISTS public.quote_line_items (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES public.price_quotes(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    line_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_quote_line_items_quote ON public.quote_line_items (quote_id);

-- 5. Extend Orders for Fulfillment Type, Deposit & Idempotency
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS fulfillment_type VARCHAR(30) NOT NULL DEFAULT 'PHYSICAL';
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS payment_type VARCHAR(30) NOT NULL DEFAULT 'FULL_PAYMENT';
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS deposit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_print_orders_idempotency ON public.print_orders (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 6. Entitlements
CREATE TABLE IF NOT EXISTS public.entitlements (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    order_item_id UUID,
    resource_id UUID NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    entitlement_type VARCHAR(50) NOT NULL, -- VIEW_HIGH_QUALITY, DOWNLOAD_DIGITAL_MODEL, EXPORT_MODEL, VIEW_AR
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REVOKED, EXPIRED
    valid_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_entitlements_owner_resource_type UNIQUE (owner_id, resource_id, entitlement_type)
);

CREATE INDEX IF NOT EXISTS idx_entitlements_owner ON public.entitlements (owner_id);
CREATE INDEX IF NOT EXISTS idx_entitlements_status ON public.entitlements (status);

-- 7. Digital Assets Management
CREATE TABLE IF NOT EXISTS public.digital_assets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    order_item_id UUID,
    job_id UUID REFERENCES public.reconstruction_jobs(id) ON DELETE SET NULL,
    file_format VARCHAR(10) NOT NULL, -- GLB, OBJ, STL
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'CLOUDINARY',
    storage_path VARCHAR(500) NOT NULL,
    delivery_url TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    sha256 VARCHAR(64),
    status VARCHAR(30) NOT NULL DEFAULT 'READY', -- PROCESSING, READY, FAILED
    download_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_digital_assets_user ON public.digital_assets (user_id);
CREATE INDEX IF NOT EXISTS idx_digital_assets_job ON public.digital_assets (job_id);

-- 8. Customization Options (BE-10)
CREATE TABLE IF NOT EXISTS public.customization_options (
    id UUID PRIMARY KEY,
    product_id UUID REFERENCES public.products(id) ON DELETE CASCADE,
    category_id UUID REFERENCES public.product_categories(id) ON DELETE SET NULL,
    option_type VARCHAR(50) NOT NULL, -- SIZE, MATERIAL, POSE, BASE, ENGRAVING, ACCESSORIES, PAINTING, PACKAGING
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    additional_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    requires_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customization_options_product ON public.customization_options (product_id);
CREATE INDEX IF NOT EXISTS idx_customization_options_active ON public.customization_options (is_active);

-- 9. Manual Quotations (BE-11)
CREATE TABLE IF NOT EXISTS public.manual_quotations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    assigned_manager_id UUID REFERENCES public.users(id) ON DELETE SET NULL,
    scan_id UUID REFERENCES public.scans(id) ON DELETE SET NULL,
    product_id UUID REFERENCES public.products(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SUBMITTED, UNDER_REVIEW, QUOTED, ACCEPTED, REJECTED, EXPIRED
    customer_notes TEXT,
    manager_notes TEXT,
    customization_spec TEXT,
    quoted_amount NUMERIC(12, 2),
    valid_until TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_manual_quotations_user ON public.manual_quotations (user_id);
CREATE INDEX IF NOT EXISTS idx_manual_quotations_status ON public.manual_quotations (status);

-- 10. Model Versions & Approvals (BE-12)
CREATE TABLE IF NOT EXISTS public.model_versions (
    id UUID PRIMARY KEY,
    scan_id UUID NOT NULL REFERENCES public.scans(id) ON DELETE CASCADE,
    job_id UUID REFERENCES public.reconstruction_jobs(id) ON DELETE SET NULL,
    version_number INT NOT NULL DEFAULT 1,
    model_url TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'MODEL_READY', -- MODEL_READY, AWAITING_APPROVAL, APPROVED, REVISION_REQUESTED, LOCKED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_model_versions_scan_num UNIQUE (scan_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_model_versions_scan ON public.model_versions (scan_id);

CREATE TABLE IF NOT EXISTS public.model_reviews (
    id UUID PRIMARY KEY,
    model_version_id UUID NOT NULL REFERENCES public.model_versions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    decision VARCHAR(30) NOT NULL, -- APPROVED, REVISION_REQUESTED
    notes TEXT,
    revision_reason VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_model_reviews_version ON public.model_reviews (model_version_id);

-- 11. Extend Print Jobs for Outsource & Model Version Linkage (BE-13)
ALTER TABLE public.print_jobs ADD COLUMN IF NOT EXISTS order_item_id UUID;
ALTER TABLE public.print_jobs ADD COLUMN IF NOT EXISTS model_version_id UUID REFERENCES public.model_versions(id) ON DELETE SET NULL;
ALTER TABLE public.print_jobs ADD COLUMN IF NOT EXISTS is_outsource BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.print_jobs ADD COLUMN IF NOT EXISTS partner_name VARCHAR(255);
ALTER TABLE public.print_jobs ADD COLUMN IF NOT EXISTS reprint_of_job_id UUID REFERENCES public.print_jobs(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_print_jobs_order_item ON public.print_jobs (order_item_id);
CREATE INDEX IF NOT EXISTS idx_print_jobs_model_version ON public.print_jobs (model_version_id);

-- 12. Physical Quality Control (BE-14)
CREATE TABLE IF NOT EXISTS public.physical_quality_checks (
    id UUID PRIMARY KEY,
    print_job_id UUID NOT NULL REFERENCES public.print_jobs(id) ON DELETE CASCADE,
    inspector_id UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'PASSED', -- PASSED, FAILED
    model_accuracy_pass BOOLEAN NOT NULL DEFAULT TRUE,
    surface_quality_pass BOOLEAN NOT NULL DEFAULT TRUE,
    dimensions_pass BOOLEAN NOT NULL DEFAULT TRUE,
    color_finishing_pass BOOLEAN NOT NULL DEFAULT TRUE,
    structural_integrity_pass BOOLEAN NOT NULL DEFAULT TRUE,
    packaging_pass BOOLEAN NOT NULL DEFAULT TRUE,
    failure_reason_code VARCHAR(100),
    notes TEXT,
    evidence_urls TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_physical_quality_checks_job ON public.physical_quality_checks (print_job_id);

-- 13. Notifications (BE-15)
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    metadata TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON public.notifications (user_id, is_read);

-- 14. Transactional Outbox (BE-03, BE-04, Cross-cutting)
CREATE TABLE IF NOT EXISTS public.transactional_outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_transactional_outbox_status ON public.transactional_outbox (status, created_at);

-- 15. Seed Pricing Rules for Default Product Tiers
INSERT INTO public.pricing_rules (id, code, name, calculation_type, modifier_amount, active, priority, created_at, updated_at)
VALUES
    ('a1111111-0000-0000-0000-000000000001', 'RULE_BASE_PREVIEW', 'Free Preview Pricing Tier', 'FIXED_PRICE', 0, true, 1, NOW(), NOW()),
    ('a1111111-0000-0000-0000-000000000002', 'RULE_PREMIUM_DIGITAL_EXPORT', 'Premium Digital Export HQ Tier', 'FIXED_PRICE', 149000, true, 2, NOW(), NOW()),
    ('a1111111-0000-0000-0000-000000000003', 'RULE_MINI_KEEPSAKE_BASE', 'Mini Keepsake Standard Pricing', 'FIXED_PRICE', 249000, true, 3, NOW(), NOW()),
    ('a1111111-0000-0000-0000-000000000004', 'RULE_STANDARD_KEEPSAKE_BASE', 'Standard Keepsake Desk Sculpture Pricing', 'FIXED_PRICE', 399000, true, 4, NOW(), NOW()),
    ('a1111111-0000-0000-0000-000000000005', 'RULE_PREMIUM_CUSTOMIZATION_BASE', 'Premium Customization Keepsake Base', 'FIXED_PRICE', 999000, true, 5, NOW(), NOW()),
    ('a1111111-0000-0000-0000-000000000006', 'RULE_CUSTOM_ENGRAVING', 'Custom Laser/Sculpt Engraving Surcharge', 'FIXED_PRICE', 50000, true, 10, NOW(), NOW()),
    ('a1111111-0000-0000-0000-000000000007', 'RULE_PREMIUM_PACKAGING', 'Luxury Silk Box & Wooden Stand Packaging', 'FIXED_PRICE', 80000, true, 11, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- =========================================================================
-- V25: Create Resin Production Details for ELEGOO Saturn 4 Ultra 16K MSLA
-- =========================================================================

CREATE TABLE IF NOT EXISTS public.resin_production_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    print_job_id UUID NOT NULL UNIQUE,
    order_id UUID,
    order_item_id UUID,
    model_version_id UUID,
    stl_asset_id UUID,
    stl_delivery_url TEXT,
    dim_x_mm NUMERIC(8,2),
    dim_y_mm NUMERIC(8,2),
    dim_z_mm NUMERIC(8,2),
    is_mesh_watertight BOOLEAN DEFAULT FALSE,
    is_hollowed BOOLEAN DEFAULT FALSE,
    has_drain_holes BOOLEAN DEFAULT FALSE,
    support_density VARCHAR(50),
    slicer_name VARCHAR(100) DEFAULT 'CHITUBOX',
    slicer_version VARCHAR(50),
    printer_profile VARCHAR(100) DEFAULT 'ELEGOO Saturn 4 Ultra 16K',
    slice_file_format VARCHAR(20),
    slice_file_url TEXT,
    slice_file_sha256 VARCHAR(64),
    preview_image_url TEXT,
    resin_type VARCHAR(100),
    resin_color VARCHAR(50),
    is_custom_painted BOOLEAN DEFAULT FALSE,
    estimated_resin_ml NUMERIC(10,2),
    estimated_print_time_seconds INTEGER,
    handoff_method VARCHAR(50),
    handoff_confirmed_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    handoff_confirmed_at TIMESTAMP WITH TIME ZONE,
    operator_confirmed_printing_at TIMESTAMP WITH TIME ZONE,
    wash_completed BOOLEAN DEFAULT FALSE,
    support_removal_completed BOOLEAN DEFAULT FALSE,
    uv_cure_completed BOOLEAN DEFAULT FALSE,
    uv_cure_duration_minutes INTEGER,
    surface_finishing_completed BOOLEAN DEFAULT FALSE,
    qc_status VARCHAR(30),
    qc_notes TEXT,
    attempt_number INTEGER DEFAULT 1,
    failure_photo_urls TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_resin_details_print_job FOREIGN KEY (print_job_id) REFERENCES public.print_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_resin_details_order FOREIGN KEY (order_id) REFERENCES public.print_orders(id) ON DELETE SET NULL,
    CONSTRAINT fk_resin_details_model_version FOREIGN KEY (model_version_id) REFERENCES public.model_versions(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_resin_details_job ON public.resin_production_details (print_job_id);
CREATE INDEX IF NOT EXISTS idx_resin_details_order ON public.resin_production_details (order_id);

ALTER TABLE public.reconstruction_jobs
    ADD COLUMN output_kind VARCHAR(40) NOT NULL DEFAULT 'assembled_preview',
    ADD COLUMN customization_ready BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE public.customization_fit_results (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES public.reconstruction_jobs(id) ON DELETE RESTRICT,
    attempt_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    scan_id UUID NOT NULL REFERENCES public.scans(id) ON DELETE RESTRICT,
    catalog_id UUID NOT NULL REFERENCES public.customization_catalogs(id) ON DELETE RESTRICT,
    policy VARCHAR(100) NOT NULL,
    base_sha256 VARCHAR(64) NOT NULL,
    manifest_sha256 VARCHAR(64) NOT NULL,
    profile_sha256 VARCHAR(64) NOT NULL,
    scene_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_custom_fit_job_attempt UNIQUE(job_id,attempt_id)
);
CREATE INDEX idx_custom_fit_user ON public.customization_fit_results(user_id,created_at DESC);
CREATE INDEX idx_custom_fit_scan ON public.customization_fit_results(scan_id);
CREATE INDEX idx_custom_fit_catalog ON public.customization_fit_results(catalog_id);
CREATE TABLE public.customization_fit_assets (
    id UUID PRIMARY KEY,
    fit_result_id UUID NOT NULL REFERENCES public.customization_fit_results(id) ON DELETE RESTRICT,
    asset_version_id UUID NOT NULL REFERENCES public.customization_asset_versions(id) ON DELETE RESTRICT,
    geometry_mode VARCHAR(30) NOT NULL CHECK(geometry_mode IN ('shared_transform','fitted_geometry')),
    storage_provider VARCHAR(30) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    location TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL CHECK(file_size_bytes>0),
    sha256 VARCHAR(64) NOT NULL,
    matrix_json JSONB NOT NULL CHECK(jsonb_typeof(matrix_json)='array' AND jsonb_array_length(matrix_json)=16),
    CONSTRAINT uq_custom_fit_asset UNIQUE(fit_result_id,asset_version_id)
);
CREATE INDEX idx_custom_fit_assets_version ON public.customization_fit_assets(asset_version_id);

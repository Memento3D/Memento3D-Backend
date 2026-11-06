-- Shared, immutable preview assets. This migration does not activate a catalog.
CREATE TABLE public.customization_catalogs (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    body_preset VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    import_sha256 VARCHAR(64),
    groups_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    default_selection_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    verified_object_count INTEGER NOT NULL DEFAULT 0 CHECK (verified_object_count >= 0),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE public.customization_asset_versions (
    id UUID PRIMARY KEY,
    asset_code VARCHAR(100) NOT NULL,
    version VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    slot VARCHAR(40) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL CHECK (file_size_bytes > 0),
    sha256 VARCHAR(64) NOT NULL,
    cloudinary_public_id VARCHAR(500) NOT NULL,
    cloudinary_version BIGINT NOT NULL,
    thumbnail_url TEXT NOT NULL,
    thumbnail_sha256 VARCHAR(64) NOT NULL,
    placement_policy VARCHAR(100) NOT NULL,
    compatibility_json JSONB NOT NULL,
    print_ready BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_custom_asset_version UNIQUE(asset_code, version)
);
CREATE TABLE public.customization_catalog_members (
    id UUID PRIMARY KEY,
    catalog_id UUID NOT NULL REFERENCES public.customization_catalogs(id) ON DELETE RESTRICT,
    asset_version_id UUID NOT NULL REFERENCES public.customization_asset_versions(id) ON DELETE RESTRICT,
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    CONSTRAINT uq_custom_catalog_member UNIQUE(catalog_id, asset_version_id)
);
CREATE INDEX idx_custom_catalog_members_asset ON public.customization_catalog_members(asset_version_id);

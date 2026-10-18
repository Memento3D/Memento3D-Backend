-- V9: Warehouse Memberships and WMS Phase 1 Access Control

-- 1. Create warehouse_memberships table
CREATE TABLE IF NOT EXISTS public.warehouse_memberships (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_by UUID,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_memberships_role CHECK (role IN ('MANAGER', 'WAREHOUSE_STAFF', 'PRINT_OPERATOR')),
    CONSTRAINT chk_memberships_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT uq_warehouse_user UNIQUE (warehouse_id, user_id),
    CONSTRAINT fk_memberships_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_assigned_by FOREIGN KEY (assigned_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_warehouse_memberships_user ON public.warehouse_memberships (user_id, status);
CREATE INDEX IF NOT EXISTS idx_warehouse_memberships_warehouse ON public.warehouse_memberships (warehouse_id, status);

-- 2. Seed default primary warehouse if not exists
INSERT INTO public.warehouses (id, code, name, location, status, created_at, updated_at)
SELECT
    'a1111111-1111-1111-1111-111111111111'::uuid,
    'WH-HCM-01',
    'Ho Chi Minh Central 3D Hub',
    'District 1, Ho Chi Minh City, Vietnam',
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM public.warehouses WHERE LOWER(code) = 'wh-hcm-01'
);

-- 3. Seed initial warehouse memberships for seeded admin and operator users
INSERT INTO public.warehouse_memberships (id, warehouse_id, user_id, role, status, assigned_at, created_at, updated_at)
SELECT
    gen_random_uuid(),
    w.id,
    u.id,
    'MANAGER',
    'ACTIVE',
    NOW(),
    NOW(),
    NOW()
FROM public.warehouses w
CROSS JOIN public.users u
WHERE LOWER(w.code) = 'wh-hcm-01'
  AND u.email = 'admin@memento3d.com'
ON CONFLICT (warehouse_id, user_id) DO NOTHING;

INSERT INTO public.warehouse_memberships (id, warehouse_id, user_id, role, status, assigned_at, created_at, updated_at)
SELECT
    gen_random_uuid(),
    w.id,
    u.id,
    'WAREHOUSE_STAFF',
    'ACTIVE',
    NOW(),
    NOW(),
    NOW()
FROM public.warehouses w
CROSS JOIN public.users u
WHERE LOWER(w.code) = 'wh-hcm-01'
  AND u.email IN ('operator@memento3d.com', 'operator@memento3d.vn')
ON CONFLICT (warehouse_id, user_id) DO NOTHING;


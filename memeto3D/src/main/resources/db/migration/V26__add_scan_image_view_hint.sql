ALTER TABLE public.scan_images
    ADD COLUMN view_hint VARCHAR(10);

ALTER TABLE public.scan_images
    ADD CONSTRAINT scan_images_view_hint_check
        CHECK (view_hint IS NULL OR view_hint IN ('front', 'left', 'right', 'back'));

CREATE UNIQUE INDEX uq_scan_images_active_scan_view_hint
    ON public.scan_images (scan_id, view_hint)
    WHERE view_hint IS NOT NULL AND deleted_at IS NULL;

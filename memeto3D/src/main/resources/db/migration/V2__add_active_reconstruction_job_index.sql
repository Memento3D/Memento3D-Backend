CREATE UNIQUE INDEX uq_reconstruction_jobs_active_scan
    ON public.reconstruction_jobs (scan_id)
    WHERE status IN ('queued', 'running');
ALTER TABLE public.reconstruction_jobs
    ADD COLUMN IF NOT EXISTS progress INTEGER;

ALTER TABLE public.reconstruction_jobs
    ADD CONSTRAINT reconstruction_jobs_progress_check
        CHECK (progress BETWEEN 0 AND 100);
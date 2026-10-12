CREATE TABLE public.model_outputs (
                                      id UUID PRIMARY KEY,
                                      job_id UUID NOT NULL,
                                      attempt_id UUID NOT NULL,
                                      format VARCHAR(10) NOT NULL,
                                      storage_provider VARCHAR(30) NOT NULL,
                                      storage_public_id VARCHAR(500) NOT NULL,
                                      storage_asset_id VARCHAR(255),
                                      delivery_url TEXT NOT NULL,
                                      file_size_bytes BIGINT NOT NULL,
                                      sha256 VARCHAR(64) NOT NULL,
                                      created_at TIMESTAMP WITH TIME ZONE NOT NULL,

                                      CONSTRAINT fk_model_outputs_job
                                          FOREIGN KEY (job_id)
                                              REFERENCES public.reconstruction_jobs (id),

                                      CONSTRAINT uq_model_outputs_job_attempt_format
                                          UNIQUE (job_id, attempt_id, format),

                                      CONSTRAINT ck_model_outputs_file_size
                                          CHECK (file_size_bytes > 0)
);

CREATE INDEX idx_model_outputs_job
    ON public.model_outputs (job_id);
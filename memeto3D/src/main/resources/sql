-- =====================================================================
--  3DForge / Memento3D - PostgreSQL schema
--  Target: PostgreSQL 13+ (gen_random_uuid() is built in)
--  Run on an EMPTY database. The whole script runs in one transaction:
--  if any statement fails, everything is rolled back.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   VARCHAR(255) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,            -- bcrypt, cost >= 10 (FR-164)
    display_name            VARCHAR(100),
    role                    VARCHAR(20) NOT NULL DEFAULT 'user'
                            CHECK (role IN ('user', 'admin', 'operator')),
    email_verified          BOOLEAN NOT NULL DEFAULT FALSE,   -- FR-001
    storage_used_bytes      BIGINT NOT NULL DEFAULT 0,        -- FR-181
    storage_quota_bytes     BIGINT NOT NULL DEFAULT 10737418240, -- 10 GB
    locale                  VARCHAR(10) DEFAULT 'en',         -- FR-078
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,                      -- FR-178 soft delete
    deletion_requested_at   TIMESTAMPTZ                       -- FR-178 30-day grace
);

CREATE INDEX idx_users_email    ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role     ON users(role)  WHERE deleted_at IS NULL;
CREATE INDEX idx_users_deletion ON users(deletion_requested_at)
    WHERE deletion_requested_at IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. refresh_tokens (access token is stateless, refresh token is stored)
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,             -- SHA-256 of refresh token
    token_family    UUID NOT NULL,                            -- rotation / reuse detection
    user_agent      TEXT,                                     -- FR-038
    ip_address      INET,
    is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,           -- FR-003
    revoked_reason  VARCHAR(50),                              -- logout, rotation, security, expired
    expires_at      TIMESTAMPTZ NOT NULL,                     -- NOW() + 7 days
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_rt_token  ON refresh_tokens(token_hash) WHERE is_revoked = FALSE;
CREATE INDEX idx_rt_user   ON refresh_tokens(user_id)    WHERE is_revoked = FALSE;
CREATE INDEX idx_rt_family ON refresh_tokens(token_family);
CREATE INDEX idx_rt_expiry ON refresh_tokens(expires_at) WHERE is_revoked = FALSE;

-- ---------------------------------------------------------------------
-- 3. token_blacklist (access token revocation)
-- ---------------------------------------------------------------------
CREATE TABLE token_blacklist (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti             VARCHAR(255) NOT NULL UNIQUE,             -- JWT ID
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason          VARCHAR(50) NOT NULL,                     -- logout, password_change, security_breach, admin_revoke
    expires_at      TIMESTAMPTZ NOT NULL,                     -- copy of token exp claim
    blacklisted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tbl_jti    ON token_blacklist(jti);
CREATE INDEX idx_tbl_expiry ON token_blacklist(expires_at);
CREATE INDEX idx_tbl_user   ON token_blacklist(user_id);

-- ---------------------------------------------------------------------
-- 4. scans
-- ---------------------------------------------------------------------
CREATE TABLE scans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,                   -- FR-005
    description      TEXT,                                    -- FR-005
    notes            TEXT,                                    -- FR-039
    input_mode       VARCHAR(10) NOT NULL DEFAULT 'image'
                     CHECK (input_mode IN ('image', 'video')),
    status           VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (status IN (
                         'draft',          -- capturing
                         'uploading',
                         'uploaded',
                         'quality_check',
                         'ready',          -- ready for reconstruction
                         'processing',
                         'done',
                         'failed',
                         'deleted'         -- FR-035
                     )),
    file_count       INTEGER NOT NULL DEFAULT 0,              -- FR-033
    total_size_bytes BIGINT NOT NULL DEFAULT 0,               -- FR-033
    device_info      JSONB,                                   -- FR-038
    thumbnail_path   VARCHAR(500),                            -- FR-114
    duplicated_from  UUID REFERENCES scans(id),               -- FR-036
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_scans_user    ON scans(user_id)   WHERE deleted_at IS NULL;
CREATE INDEX idx_scans_status  ON scans(status)    WHERE deleted_at IS NULL;
CREATE INDEX idx_scans_created ON scans(user_id, created_at DESC) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- 5. scan_images
-- ---------------------------------------------------------------------
CREATE TABLE scan_images (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id           UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    storage_path      VARCHAR(500) NOT NULL,                  -- FR-166 private storage
    file_size_bytes   BIGINT NOT NULL,                        -- FR-030
    mime_type         VARCHAR(50) NOT NULL,                   -- FR-180
    width             INTEGER,                                -- FR-049
    height            INTEGER,
    format            VARCHAR(10) NOT NULL
                      CHECK (format IN ('jpg', 'jpeg', 'png')), -- FR-028
    sort_order        INTEGER NOT NULL DEFAULT 0,             -- FR-017
    exif_data         JSONB,                                  -- FR-057
    hash_md5          VARCHAR(32),                            -- FR-045
    hash_perceptual   VARCHAR(64),                            -- FR-046
    is_excluded       BOOLEAN NOT NULL DEFAULT FALSE,         -- FR-055
    exclude_reason    VARCHAR(100),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_scan_images_scan     ON scan_images(scan_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_scan_images_hash     ON scan_images(hash_md5);
CREATE INDEX idx_scan_images_excluded ON scan_images(scan_id, is_excluded) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- 6. scan_videos
-- ---------------------------------------------------------------------
CREATE TABLE scan_videos (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id           UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    storage_path      VARCHAR(500) NOT NULL,
    file_size_bytes   BIGINT NOT NULL,                        -- FR-031 max 2GB
    mime_type         VARCHAR(50) NOT NULL,                   -- FR-180
    format            VARCHAR(10) NOT NULL
                      CHECK (format IN ('mp4', 'mov', 'avi')), -- FR-029
    duration_seconds  DECIMAL(10,2),                          -- FR-060
    fps               DECIMAL(6,2),                           -- FR-061
    width             INTEGER,
    height            INTEGER,
    thumbnail_path    VARCHAR(500),                           -- FR-022
    frames_extracted  INTEGER,                                -- FR-083
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_scan_videos_scan ON scan_videos(scan_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- 7. upload_chunks
-- ---------------------------------------------------------------------
CREATE TABLE upload_chunks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id          UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    file_reference   VARCHAR(255) NOT NULL,                   -- filename or upload session id
    chunk_index      INTEGER NOT NULL,
    chunk_size_bytes BIGINT NOT NULL,
    storage_path     VARCHAR(500),
    status           VARCHAR(20) NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending', 'uploaded', 'failed', 'cancelled')),
    uploaded_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (scan_id, file_reference, chunk_index)
);

CREATE INDEX idx_upload_chunks_scan ON upload_chunks(scan_id, file_reference);

-- ---------------------------------------------------------------------
-- 8. quality_check_runs
-- ---------------------------------------------------------------------
CREATE TABLE quality_check_runs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id            UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    run_number         INTEGER NOT NULL DEFAULT 1,            -- FR-073
    algo_version       VARCHAR(50) NOT NULL,                  -- FR-064
    status             VARCHAR(20) NOT NULL DEFAULT 'running'
                       CHECK (status IN ('running', 'completed', 'failed')),

    total_images       INTEGER NOT NULL DEFAULT 0,
    passed_images      INTEGER NOT NULL DEFAULT 0,
    failed_images      INTEGER NOT NULL DEFAULT 0,
    overall_score      DECIMAL(5,2),                          -- FR-051: 0-100
    score_category     VARCHAR(10)
                       CHECK (score_category IN ('green', 'yellow', 'red')),

    difficulty_score   VARCHAR(10)                            -- FR-080
                       CHECK (difficulty_score IN ('easy', 'medium', 'hard')),
    difficulty_detail  JSONB,                                 -- {feature_density, overlap_ratio, excluded_pct}
    coverage_analysis  JSONB,                                 -- FR-058
    suggestion         TEXT,                                  -- FR-072

    processing_time_ms INTEGER,                               -- FR-077
    started_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ,
    error_message      TEXT,                                  -- FR-068
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (scan_id, run_number)
);

CREATE INDEX idx_qc_runs_scan ON quality_check_runs(scan_id);

-- ---------------------------------------------------------------------
-- 9. image_quality_results
-- ---------------------------------------------------------------------
CREATE TABLE image_quality_results (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quality_check_run_id     UUID NOT NULL REFERENCES quality_check_runs(id) ON DELETE CASCADE,
    scan_image_id            UUID NOT NULL REFERENCES scan_images(id) ON DELETE CASCADE,

    -- FR-041 sharpness / blur
    sharpness_score          DECIMAL(10,2),
    is_blurry                BOOLEAN NOT NULL DEFAULT FALSE,
    blur_type                VARCHAR(20),                     -- motion_blur, defocus_blur

    -- FR-043, FR-044 brightness / exposure
    mean_brightness          DECIMAL(6,2),
    is_too_dark              BOOLEAN NOT NULL DEFAULT FALSE,
    is_too_bright            BOOLEAN NOT NULL DEFAULT FALSE,
    saturated_pixel_pct      DECIMAL(5,2),
    has_exposure_warning     BOOLEAN NOT NULL DEFAULT FALSE,

    -- FR-047 features
    feature_count            INTEGER,
    is_low_features          BOOLEAN NOT NULL DEFAULT FALSE,

    -- FR-048 overlap
    overlap_score_prev       DECIMAL(5,2),
    overlap_score_next       DECIMAL(5,2),
    has_insufficient_overlap BOOLEAN NOT NULL DEFAULT FALSE,

    -- FR-045, FR-046 duplicates
    is_exact_duplicate       BOOLEAN NOT NULL DEFAULT FALSE,
    is_near_duplicate        BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_group_id       UUID,

    -- FR-050 aspect ratio
    aspect_ratio_warning     BOOLEAN NOT NULL DEFAULT FALSE,

    -- FR-075 watermark
    has_watermark_warning    BOOLEAN NOT NULL DEFAULT FALSE,
    watermark_area_pct       DECIMAL(5,2),

    -- FR-076 noise
    snr_score                DECIMAL(8,2),
    is_high_noise            BOOLEAN NOT NULL DEFAULT FALSE,

    overall_status           VARCHAR(10) NOT NULL DEFAULT 'pass'
                             CHECK (overall_status IN ('pass', 'warn', 'fail')),
    failure_reasons          TEXT[],                          -- FR-053

    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iqr_run    ON image_quality_results(quality_check_run_id);
CREATE INDEX idx_iqr_image  ON image_quality_results(scan_image_id);
CREATE INDEX idx_iqr_status ON image_quality_results(overall_status);

-- ---------------------------------------------------------------------
-- 10. reconstruction_jobs
-- ---------------------------------------------------------------------
CREATE TABLE reconstruction_jobs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id                     UUID NOT NULL UNIQUE REFERENCES scans(id) ON DELETE CASCADE, -- UNIQUE = FR-199 idempotency
    user_id                     UUID NOT NULL REFERENCES users(id),

    status                      VARCHAR(20) NOT NULL DEFAULT 'queued'
                                CHECK (status IN (
                                    'queued',
                                    'extracting',
                                    'detecting',
                                    'matching',
                                    'sfm',
                                    'mvs',
                                    'meshing',
                                    'texturing',
                                    'repairing',
                                    'exporting',
                                    'done',
                                    'failed',
                                    'cancelled',        -- FR-113
                                    'partial_success'   -- FR-088
                                )),
    queue_position              INTEGER,                      -- FR-082
    worker_id                   VARCHAR(100),                 -- FR-110

    toolchain_config            JSONB NOT NULL DEFAULT '{}',  -- FR-100, FR-116
    ab_test_group               VARCHAR(5),                   -- A, B, NULL

    input_image_count           INTEGER NOT NULL DEFAULT 0,
    qualified_image_count       INTEGER,

    cameras_registered          INTEGER,                      -- FR-087
    cameras_total               INTEGER,
    camera_register_pct         DECIMAL(5,2),                 -- FR-088 >= 70%

    sparse_point_count          BIGINT,                       -- FR-120
    dense_point_count           BIGINT,

    vertex_count                BIGINT,                       -- FR-096
    face_count                  BIGINT,
    bounding_box                JSONB,                        -- {width, height, depth} mm
    is_watertight               BOOLEAN,                      -- FR-097
    boundary_edges_count        INTEGER,

    holes_found                 INTEGER,                      -- FR-092..095
    holes_filled                INTEGER,
    non_manifold_count          INTEGER,
    isolated_components_removed INTEGER,
    self_intersection_count     INTEGER,
    is_print_ready              BOOLEAN,

    printability_status         VARCHAR(20)                   -- FR-109
                                CHECK (printability_status IN ('pass', 'fail', 'warn', 'not_checked')),
    printability_detail         JSONB,

    estimated_time_seconds      INTEGER,                      -- FR-111
    total_processing_seconds    INTEGER,                      -- FR-115
    processing_time_per_stage   JSONB,                        -- {stage: seconds}

    retry_count                 INTEGER NOT NULL DEFAULT 0,   -- FR-104
    max_retries                 INTEGER NOT NULL DEFAULT 3,
    last_failed_stage           VARCHAR(20),

    error_message               TEXT,                         -- FR-103
    error_stage                 VARCHAR(20),

    queued_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,                  -- FR-113
    cancelled_by                UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rjobs_user   ON reconstruction_jobs(user_id);
CREATE INDEX idx_rjobs_status ON reconstruction_jobs(status);
CREATE INDEX idx_rjobs_queue  ON reconstruction_jobs(queue_position) WHERE status = 'queued';
CREATE INDEX idx_rjobs_ab     ON reconstruction_jobs(ab_test_group) WHERE ab_test_group IS NOT NULL;

-- ---------------------------------------------------------------------
-- 11. job_stage_logs
-- ---------------------------------------------------------------------
CREATE TABLE job_stage_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id           UUID NOT NULL REFERENCES reconstruction_jobs(id) ON DELETE CASCADE,
    stage            VARCHAR(30) NOT NULL,
    status           VARCHAR(10) NOT NULL
                     CHECK (status IN ('started', 'completed', 'failed', 'timeout', 'skipped')),
    log_level        VARCHAR(10) NOT NULL DEFAULT 'INFO'
                     CHECK (log_level IN ('DEBUG', 'INFO', 'WARN', 'ERROR')),
    message          TEXT,
    error_detail     TEXT,
    resource_usage   JSONB,                                   -- FR-112 {cpu_pct, ram_mb, disk_gb}
    started_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at         TIMESTAMPTZ,
    duration_seconds DECIMAL(10,2),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jsl_job   ON job_stage_logs(job_id);
CREATE INDEX idx_jsl_stage ON job_stage_logs(job_id, stage);
CREATE INDEX idx_jsl_level ON job_stage_logs(log_level) WHERE log_level IN ('WARN', 'ERROR');

-- ---------------------------------------------------------------------
-- 12. reconstruction_metrics
-- ---------------------------------------------------------------------
CREATE TABLE reconstruction_metrics (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id           UUID NOT NULL REFERENCES reconstruction_jobs(id) ON DELETE CASCADE,
    scan_id          UUID NOT NULL REFERENCES scans(id),
    stage            VARCHAR(30) NOT NULL,
    duration_seconds DECIMAL(10,2) NOT NULL,
    image_count      INTEGER,
    result_quality   JSONB,
    cpu_peak_pct     DECIMAL(5,2),
    ram_peak_mb      INTEGER,
    disk_used_mb     INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rmetrics_job   ON reconstruction_metrics(job_id);
CREATE INDEX idx_rmetrics_scan  ON reconstruction_metrics(scan_id);
CREATE INDEX idx_rmetrics_stage ON reconstruction_metrics(stage);

-- ---------------------------------------------------------------------
-- 13. model_outputs
-- ---------------------------------------------------------------------
CREATE TABLE model_outputs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id         UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    job_id          UUID NOT NULL REFERENCES reconstruction_jobs(id),
    format          VARCHAR(15) NOT NULL
                    CHECK (format IN ('glb', 'stl', 'obj', 'mtl', 'texture_zip', 'ply')),
    storage_path    VARCHAR(500) NOT NULL,                    -- /{user_id}/{scan_id}/output/{filename}
    file_size_bytes BIGINT NOT NULL,
    file_hash       VARCHAR(64),                              -- SHA-256
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_model_outputs_scan   ON model_outputs(scan_id);
CREATE INDEX idx_model_outputs_format ON model_outputs(scan_id, format);

-- ---------------------------------------------------------------------
-- 14. preview_links
-- ---------------------------------------------------------------------
CREATE TABLE preview_links (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id        UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id),
    token          VARCHAR(255) NOT NULL UNIQUE,
    is_public      BOOLEAN NOT NULL DEFAULT FALSE,            -- FR-128
    allow_embed    BOOLEAN NOT NULL DEFAULT FALSE,            -- FR-155
    show_watermark BOOLEAN NOT NULL DEFAULT TRUE,             -- FR-152
    last_viewed_at TIMESTAMPTZ,                               -- FR-151
    view_count     INTEGER NOT NULL DEFAULT 0,
    expires_at     TIMESTAMPTZ,
    disabled_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_preview_links_token  ON preview_links(token);
CREATE INDEX idx_preview_links_scan   ON preview_links(scan_id);
CREATE INDEX idx_preview_links_expiry ON preview_links(last_viewed_at)
    WHERE disabled_at IS NULL AND expires_at IS NOT NULL;

-- ---------------------------------------------------------------------
-- 15. download_logs
-- ---------------------------------------------------------------------
CREATE TABLE download_logs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id),
    scan_id            UUID NOT NULL REFERENCES scans(id),
    model_output_id    UUID NOT NULL REFERENCES model_outputs(id),
    format             VARCHAR(10) NOT NULL,
    file_size_bytes    BIGINT,
    ip_address         INET,                                  -- FR-136
    signed_url_token   VARCHAR(255),                          -- FR-135
    signed_url_expires TIMESTAMPTZ,
    downloaded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_download_user ON download_logs(user_id);
CREATE INDEX idx_download_scan ON download_logs(scan_id);
CREATE INDEX idx_download_date ON download_logs(downloaded_at);

-- ---------------------------------------------------------------------
-- 16. payments
--     order_id FK is added AFTER print_orders exists (circular reference)
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(id),
    scan_id                UUID REFERENCES scans(id),
    order_id               UUID,
    payment_type           VARCHAR(20) NOT NULL
                           CHECK (payment_type IN ('download', 'print_order')),
    amount                 DECIMAL(12,2) NOT NULL,
    currency               VARCHAR(3) NOT NULL DEFAULT 'VND',
    payment_gateway        VARCHAR(50),                       -- VNPay, Stripe, ...
    gateway_transaction_id VARCHAR(255),
    status                 VARCHAR(20) NOT NULL DEFAULT 'pending'
                           CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'refunded')),
    refund_reason          TEXT,                              -- FR-147
    refunded_at            TIMESTAMPTZ,
    paid_at                TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_user   ON payments(user_id);
CREATE INDEX idx_payments_scan   ON payments(scan_id);
CREATE INDEX idx_payments_order  ON payments(order_id);
CREATE INDEX idx_payments_status ON payments(status);

-- ---------------------------------------------------------------------
-- 17. print_orders
-- ---------------------------------------------------------------------
CREATE TABLE print_orders (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES users(id),
    scan_id              UUID NOT NULL REFERENCES scans(id),
    payment_id           UUID REFERENCES payments(id),
    model_output_id      UUID NOT NULL REFERENCES model_outputs(id), -- STL file

    material             VARCHAR(50) NOT NULL,                -- PLA, ABS, PETG
    color                VARCHAR(50),

    scale_factor         DECIMAL(5,2) NOT NULL DEFAULT 1.0,   -- FR-156
    scaled_dimensions    JSONB,                               -- {width_cm, height_cm, depth_cm}

    estimated_volume_cm3 DECIMAL(10,2),                       -- FR-142
    material_rate        DECIMAL(10,2),
    service_fee          DECIMAL(12,2),
    total_price          DECIMAL(12,2) NOT NULL,

    recipient_name       VARCHAR(255) NOT NULL,               -- FR-141
    shipping_address     TEXT NOT NULL,
    city                 VARCHAR(100) NOT NULL,
    phone                VARCHAR(20) NOT NULL,
    shipping_notes       TEXT,

    status               VARCHAR(20) NOT NULL DEFAULT 'pending_payment'
                         CHECK (status IN (
                             'pending_payment',
                             'confirmed',
                             'printing',
                             'shipped',
                             'delivered',
                             'cancelled',
                             'refunded'
                         )),
    tracking_number      VARCHAR(100),                        -- FR-146
    estimated_delivery   DATE,

    cancellable_until    TIMESTAMPTZ,                         -- FR-147 (2h after order)
    cancelled_at         TIMESTAMPTZ,
    cancel_reason        TEXT,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user  ON print_orders(user_id);
CREATE INDEX idx_orders_scan  ON print_orders(scan_id);
CREATE INDEX idx_orders_status ON print_orders(status);
CREATE INDEX idx_orders_cancellable ON print_orders(cancellable_until)
    WHERE status = 'confirmed' AND cancelled_at IS NULL;

-- Close the circular reference payments <-> print_orders
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_order
    FOREIGN KEY (order_id) REFERENCES print_orders(id);

-- ---------------------------------------------------------------------
-- 18. order_status_history
-- ---------------------------------------------------------------------
CREATE TABLE order_status_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID NOT NULL REFERENCES print_orders(id) ON DELETE CASCADE,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by UUID REFERENCES users(id),
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_osh_order ON order_status_history(order_id);
CREATE INDEX idx_osh_date  ON order_status_history(created_at);

-- ---------------------------------------------------------------------
-- 19. model_ratings
-- ---------------------------------------------------------------------
CREATE TABLE model_ratings (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID REFERENCES users(id),
    scan_id    UUID NOT NULL REFERENCES scans(id),
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, scan_id)
);

CREATE INDEX idx_ratings_scan ON model_ratings(scan_id);

-- ---------------------------------------------------------------------
-- 20. audit_logs (admin-only access, never deleted by normal users)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users(id),                  -- NULL = system action
    action        VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id   UUID,
    ip_address    INET,
    user_agent    TEXT,
    details       JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user     ON audit_logs(user_id);
CREATE INDEX idx_audit_action   ON audit_logs(action);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_date     ON audit_logs(created_at);

-- ---------------------------------------------------------------------
-- 21. system_configs
-- ---------------------------------------------------------------------
CREATE TABLE system_configs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    value_type   VARCHAR(20) NOT NULL DEFAULT 'string'
                 CHECK (value_type IN ('string', 'integer', 'decimal', 'boolean', 'json')),
    description  TEXT,
    updated_by   UUID REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- 22. daily_stats
-- ---------------------------------------------------------------------
CREATE TABLE daily_stats (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stat_date           DATE NOT NULL UNIQUE,
    total_jobs          INTEGER NOT NULL DEFAULT 0,
    success_count       INTEGER NOT NULL DEFAULT 0,
    fail_count          INTEGER NOT NULL DEFAULT 0,
    success_rate_pct    DECIMAL(5,2),                         -- FR-189
    total_scans_created INTEGER NOT NULL DEFAULT 0,
    total_downloads     INTEGER NOT NULL DEFAULT 0,
    total_orders        INTEGER NOT NULL DEFAULT 0,
    total_revenue       DECIMAL(14,2) NOT NULL DEFAULT 0,
    active_users        INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_daily_stats_date ON daily_stats(stat_date);

-- ---------------------------------------------------------------------
-- 23. notifications
--     type: registration_confirm, reconstruction_done, reconstruction_failed,
--           order_confirmed, order_status_update, data_deletion_warning,
--           queue_alert, failure_rate_alert
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID REFERENCES users(id),                     -- NULL = admin system alert
    type       VARCHAR(50) NOT NULL,
    channel    VARCHAR(20) NOT NULL DEFAULT 'email'
               CHECK (channel IN ('email', 'in_app', 'push')),
    subject    VARCHAR(255),
    body       TEXT,
    metadata   JSONB,                                         -- {scan_id, job_id, order_id, ...}
    status     VARCHAR(20) NOT NULL DEFAULT 'pending'
               CHECK (status IN ('pending', 'sent', 'failed', 'read')),
    sent_at    TIMESTAMPTZ,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user   ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status) WHERE status = 'pending';
CREATE INDEX idx_notifications_type   ON notifications(type);

-- ---------------------------------------------------------------------
-- 24. outbox_events (Transactional Outbox for Kafka)
-- ---------------------------------------------------------------------
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,                      -- scan, job, order, user, payment
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    partition_key  VARCHAR(255),
    payload        JSONB NOT NULL,
    metadata       JSONB,                                     -- {correlation_id, causation_id, user_id, ip}
    status         VARCHAR(20) NOT NULL DEFAULT 'pending'
                   CHECK (status IN ('pending', 'published', 'failed')),
    retry_count    INTEGER NOT NULL DEFAULT 0,
    max_retries    INTEGER NOT NULL DEFAULT 5,
    last_error     TEXT,
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending   ON outbox_events(created_at) WHERE status = 'pending';
CREATE INDEX idx_outbox_failed    ON outbox_events(created_at) WHERE status = 'failed' AND retry_count < max_retries;
CREATE INDEX idx_outbox_cleanup   ON outbox_events(published_at) WHERE status = 'published';
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- ---------------------------------------------------------------------
-- 25. dead_letter_events (Kafka DLQ)
-- ---------------------------------------------------------------------
CREATE TABLE dead_letter_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_event_id  UUID REFERENCES outbox_events(id),     -- NULL if external source
    original_topic     VARCHAR(100) NOT NULL,
    original_partition INTEGER,
    original_offset    BIGINT,
    event_type         VARCHAR(100) NOT NULL,
    partition_key      VARCHAR(255),
    payload            JSONB NOT NULL,
    error_message      TEXT NOT NULL,
    error_stack_trace  TEXT,
    consumer_group     VARCHAR(100) NOT NULL,
    retry_count        INTEGER NOT NULL DEFAULT 0,
    status             VARCHAR(20) NOT NULL DEFAULT 'unresolved'
                       CHECK (status IN ('unresolved', 'retrying', 'resolved', 'discarded')),
    resolved_by        UUID REFERENCES users(id),
    resolved_at        TIMESTAMPTZ,
    resolution_note    TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dle_status ON dead_letter_events(status) WHERE status = 'unresolved';
CREATE INDEX idx_dle_topic  ON dead_letter_events(original_topic);
CREATE INDEX idx_dle_type   ON dead_letter_events(event_type);
CREATE INDEX idx_dle_date   ON dead_letter_events(created_at);

-- ---------------------------------------------------------------------
-- 26. consumed_event_ids (consumer idempotency)
-- ---------------------------------------------------------------------
CREATE TABLE consumed_event_ids (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       UUID NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    topic          VARCHAR(100) NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (event_id, consumer_group)
);

CREATE INDEX idx_cei_lookup  ON consumed_event_ids(event_id, consumer_group);
CREATE INDEX idx_cei_cleanup ON consumed_event_ids(processed_at);

-- ---------------------------------------------------------------------
-- Kafka topics mapping (documentation only)
--   3dforge.user.events            user.registered, user.verified, user.deleted
--   3dforge.scan.events            scan.created, scan.uploaded, scan.deleted
--   3dforge.qc.events              qc.started, qc.completed, qc.failed
--   3dforge.reconstruction.events  job.queued, job.stage_changed, job.completed, job.failed
--   3dforge.model.events           model.exported, model.downloaded
--   3dforge.order.events           order.created, order.paid, order.status_changed, order.cancelled
--   3dforge.payment.events         payment.completed, payment.failed, payment.refunded
--   3dforge.system.alerts          alert.queue_overflow, alert.high_failure_rate, alert.disk_low
-- ---------------------------------------------------------------------

-- =====================================================================
-- VIEWS
-- =====================================================================

-- User dashboard (FR-004, FR-154)
CREATE VIEW v_scan_summary AS
SELECT
    s.id AS scan_id,
    s.user_id,
    s.name,
    s.status,
    s.input_mode,
    s.file_count,
    s.total_size_bytes,
    s.thumbnail_path,
    s.created_at,
    rj.status AS job_status,
    rj.is_print_ready,
    rj.printability_status,
    rj.total_processing_seconds,
    qcr.overall_score AS quality_score,
    qcr.score_category AS quality_category,
    COALESCE(r.avg_rating, 0) AS avg_rating
FROM scans s
LEFT JOIN reconstruction_jobs rj ON rj.scan_id = s.id
LEFT JOIN LATERAL (
    SELECT overall_score, score_category
    FROM quality_check_runs
    WHERE scan_id = s.id
    ORDER BY run_number DESC
    LIMIT 1
) qcr ON TRUE
LEFT JOIN LATERAL (
    SELECT AVG(rating)::DECIMAL(3,1) AS avg_rating
    FROM model_ratings
    WHERE scan_id = s.id
) r ON TRUE
WHERE s.deleted_at IS NULL;

-- Admin dashboard (FR-158, FR-170)
CREATE VIEW v_admin_dashboard AS
SELECT
    (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL) AS total_users,
    (SELECT COUNT(*) FROM scans WHERE deleted_at IS NULL) AS total_scans,
    (SELECT COUNT(*) FROM reconstruction_jobs WHERE status = 'done') AS successful_jobs,
    (SELECT COUNT(*) FROM reconstruction_jobs WHERE status = 'failed') AS failed_jobs,
    (SELECT COUNT(*) FROM reconstruction_jobs WHERE status IN (
        'queued', 'extracting', 'detecting', 'matching',
        'sfm', 'mvs', 'meshing', 'texturing', 'repairing', 'exporting'
    )) AS active_jobs,
    (SELECT COUNT(*) FROM reconstruction_jobs WHERE status = 'queued') AS queue_depth,
    (SELECT COUNT(*) FROM print_orders
        WHERE status NOT IN ('cancelled', 'refunded')) AS total_orders,
    (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'completed') AS total_revenue;

-- Order management (FR-149)
CREATE VIEW v_order_management AS
SELECT
    po.id AS order_id,
    po.status,
    po.material,
    po.color,
    po.total_price,
    po.tracking_number,
    po.created_at AS order_date,
    po.recipient_name,
    po.city,
    u.email AS user_email,
    s.name AS scan_name,
    mo.storage_path AS stl_file_path,
    mo.file_size_bytes AS stl_file_size
FROM print_orders po
JOIN users u ON u.id = po.user_id
JOIN scans s ON s.id = po.scan_id
JOIN model_outputs mo ON mo.id = po.model_output_id
ORDER BY po.created_at DESC;

-- =====================================================================
-- SEED: system_configs
-- =====================================================================
INSERT INTO system_configs (config_key, config_value, value_type, description) VALUES
-- Quality Check thresholds (FR-042, FR-067)
('qc.blur_threshold',                 '100',        'decimal',  'Laplacian variance threshold for blur detection'),
('qc.brightness_min',                 '40',         'integer',  'Minimum mean brightness (0-255)'),
('qc.brightness_max',                 '220',        'integer',  'Maximum mean brightness (0-255)'),
('qc.saturated_pixel_pct_max',        '5.0',        'decimal',  'Max % saturated pixels before exposure warning'),
('qc.feature_count_min',              '500',        'integer',  'Minimum keypoints per image'),
('qc.overlap_min_pct',                '30',         'decimal',  'Minimum overlap between adjacent images (%)'),
('qc.near_duplicate_threshold',       '95',         'decimal',  'pHash similarity % for near-duplicate'),
('qc.min_resolution_width',           '1280',       'integer',  'Minimum width in px (FR-049)'),
('qc.min_resolution_height',          '720',        'integer',  'Minimum height in px (FR-049)'),
('qc.min_images_for_reconstruction',  '10',         'integer',  'Minimum images after filtering (FR-056)'),
('qc.snr_threshold',                  '20',         'decimal',  'SNR threshold for noise detection'),

-- Upload limits (FR-030, FR-031)
('upload.max_image_size_bytes',       '20971520',   'integer',  'Max image size 20MB'),
('upload.max_video_size_bytes',       '2147483648', 'integer',  'Max video size 2GB'),
('upload.min_video_duration_sec',     '10',         'integer',  'Min video duration (FR-060)'),

-- Reconstruction (FR-100, FR-103, FR-104)
('recon.sfm_tool',                    'colmap',     'string',   'SfM tool: colmap/openmvg'),
('recon.mvs_tool',                    'openmvs',    'string',   'MVS tool: openmvs/colmap_dense'),
('recon.timeout_per_stage_sec',       '3600',       'integer',  'Timeout per stage (1h default)'),
('recon.max_retries',                 '3',          'integer',  'Max retry per failed stage'),
('recon.video_frame_interval_sec',    '0.5',        'decimal',  'Frame extraction interval (FR-083)'),
('recon.mesh_max_faces',              '1000000',    'integer',  'Max faces for simplification (FR-098)'),
('recon.camera_register_min_pct',     '70',         'decimal',  'Min camera registered % (FR-088)'),
('recon.ab_test_enabled',             'false',      'boolean',  'Enable A/B testing (FR-116)'),

-- Storage & Retention (FR-172, FR-177, FR-181)
('storage.user_quota_bytes',          '10737418240','integer',  'Default user quota 10GB'),
('storage.retention_days',            '90',         'integer',  'Data retention period (FR-177)'),
('storage.temp_cleanup_days',         '7',          'integer',  'Temp file cleanup (FR-172)'),
('storage.preview_inactive_days',     '30',         'integer',  'Preview link inactivity timeout (FR-151)'),

-- Security (FR-163)
('security.rate_limit_per_minute',    '60',         'integer',  'API rate limit per user/IP'),
('security.download_link_ttl_hours',  '1',          'integer',  'Signed URL TTL (FR-135)'),

-- Monitoring (FR-169, FR-191, FR-197)
('monitor.queue_alert_threshold',     '20',         'integer',  'Queue length alert threshold'),
('monitor.failure_rate_alert_pct',    '30',         'decimal',  'Failure rate alert threshold (%)'),
('monitor.min_disk_space_gb',         '10',         'integer',  'Min disk space to accept jobs (FR-197)'),

-- JWT
('jwt.access_token_ttl_minutes',      '15',         'integer',  'Access token TTL (stateless, not stored in DB)'),
('jwt.refresh_token_ttl_days',        '7',          'integer',  'Refresh token TTL (stored in refresh_tokens)'),
('jwt.token_family_reuse_detection',  'true',       'boolean',  'Enable token family rotation attack detection'),
('jwt.blacklist_cleanup_interval_hours','1',        'integer',  'Cleanup expired blacklisted tokens every X hours'),

-- Kafka
('kafka.outbox_poller_interval_ms',   '1000',       'integer',  'Outbox poller interval (ms)'),
('kafka.outbox_batch_size',           '100',        'integer',  'Max events per outbox poll batch'),
('kafka.outbox_cleanup_days',         '7',          'integer',  'Delete published outbox events after X days'),
('kafka.dlq_max_retries',             '5',          'integer',  'Max retries before sending to DLQ'),
('kafka.consumer_idempotency_retention_days','30',  'integer',  'Delete consumed_event_ids after X days');

-- =====================================================================
-- ADDITIONAL COMPOSITE INDEXES
-- =====================================================================

-- User scan listing with status filter (FR-004, FR-154)
CREATE INDEX idx_scans_user_status_date ON scans(user_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

-- Admin job monitoring (FR-170)
CREATE INDEX idx_rjobs_status_queue ON reconstruction_jobs(status, queued_at)
    WHERE status IN ('queued', 'extracting', 'detecting', 'matching',
                     'sfm', 'mvs', 'meshing');

-- Cleanup scheduled tasks (FR-172)
CREATE INDEX idx_scans_cleanup ON scans(updated_at)
    WHERE status = 'deleted' OR deleted_at IS NOT NULL;

-- Retention policy (FR-177)
CREATE INDEX idx_scans_retention ON scans(updated_at)
    WHERE deleted_at IS NULL;

-- Partitioning (audit_logs, job_stage_logs, download_logs, outbox_events,
-- consumed_event_ids) is a later optimisation: the primary key would have to
-- include the partition column, so it is intentionally NOT applied here.

COMMIT;
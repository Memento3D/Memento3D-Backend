-- =====================================================================
--  Memento3D / 3DForge - Seed Data Initialization Script (data.sql)
-- =====================================================================

-- Ensure standalone tables exist if running on a fresh DB before init_schema.sql
CREATE TABLE IF NOT EXISTS system_configs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    value_type   VARCHAR(20) NOT NULL DEFAULT 'string'
                 CHECK (value_type IN ('string', 'integer', 'decimal', 'boolean', 'json')),
    description  TEXT,
    updated_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS daily_stats (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stat_date           DATE NOT NULL UNIQUE,
    total_jobs          INTEGER NOT NULL DEFAULT 0,
    success_count       INTEGER NOT NULL DEFAULT 0,
    fail_count          INTEGER NOT NULL DEFAULT 0,
    success_rate_pct    DECIMAL(5,2),
    total_scans_created INTEGER NOT NULL DEFAULT 0,
    total_downloads     INTEGER NOT NULL DEFAULT 0,
    total_orders        INTEGER NOT NULL DEFAULT 0,
    total_revenue       DECIMAL(14,2) NOT NULL DEFAULT 0,
    active_users        INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- 1. System Configs (system_configs)
-- ---------------------------------------------------------------------
INSERT INTO system_configs (id, config_key, config_value, value_type, description, created_at, updated_at) VALUES
('c1000000-0000-0000-0000-000000000001', 'qc.blur_threshold',                 '100',        'decimal',  'Laplacian variance threshold for blur detection', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000002', 'qc.brightness_min',                 '40',         'integer',  'Minimum mean brightness (0-255)', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000003', 'qc.brightness_max',                 '220',        'integer',  'Maximum mean brightness (0-255)', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000004', 'qc.saturated_pixel_pct_max',        '5.0',        'decimal',  'Max % saturated pixels before exposure warning', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000005', 'qc.feature_count_min',              '500',        'integer',  'Minimum keypoints per image', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000006', 'qc.overlap_min_pct',                '30',         'decimal',  'Minimum overlap between adjacent images (%)', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000007', 'qc.near_duplicate_threshold',       '95',         'decimal',  'pHash similarity % for near-duplicate', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000008', 'qc.min_resolution_width',           '1280',       'integer',  'Minimum width in px', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000009', 'qc.min_resolution_height',          '720',        'integer',  'Minimum height in px', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000010', 'qc.min_images_for_reconstruction',  '10',         'integer',  'Minimum images after filtering', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000011', 'qc.snr_threshold',                  '20',         'decimal',  'SNR threshold for noise detection', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000012', 'upload.max_image_size_bytes',       '20971520',   'integer',  'Max image size 20MB', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000013', 'upload.max_video_size_bytes',       '2147483648', 'integer',  'Max video size 2GB', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000014', 'upload.min_video_duration_sec',     '10',         'integer',  'Min video duration', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000015', 'recon.sfm_tool',                    'colmap',     'string',   'SfM tool: colmap/openmvg', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000016', 'recon.mvs_tool',                    'openmvs',    'string',   'MVS tool: openmvs/colmap_dense', NOW(), NOW()),
('c1000000-0000-0000-0000-000000000017', 'storage.user_quota_bytes',          '10737418240','integer',  'Default user quota 10GB', NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Initial Seed Users (users)
-- Passwords BCrypt encoded:
--   admin@memento3d.com    / Admin@123456
--   operator@memento3d.com / Operator@123456
--   user@memento3d.com     / User@123456
--   demo@memento3d.com     / Demo@123456
-- ---------------------------------------------------------------------
INSERT INTO users (id, email, password, full_name, role, storage_used_bytes, storage_quota_bytes, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'admin@memento3d.com',    '$2a$10$w09T4XyBvB9tD9CgZ9570e3qCjGfV2T9T1T/4M0C6GgE3.v6z6.Wi', 'System Admin', 'ROLE_ADMIN',    0, 53687091200, NOW() - INTERVAL '30 days', NOW()),
('22222222-2222-2222-2222-222222222222', 'operator@memento3d.com', '$2a$10$w09T4XyBvB9tD9CgZ9570e3qCjGfV2T9T1T/4M0C6GgE3.v6z6.Wi', '3D Operator',  'ROLE_OPERATOR', 0, 21474836480, NOW() - INTERVAL '20 days', NOW()),
('33333333-3333-3333-3333-333333333333', 'user@memento3d.com',     '$2a$10$w09T4XyBvB9tD9CgZ9570e3qCjGfV2T9T1T/4M0C6GgE3.v6z6.Wi', 'John Doe',     'ROLE_USER',     252200000, 10737418240, NOW() - INTERVAL '10 days', NOW()),
('44444444-4444-4444-4444-444444444444', 'demo@memento3d.com',     '$2a$10$w09T4XyBvB9tD9CgZ9570e3qCjGfV2T9T1T/4M0C6GgE3.v6z6.Wi', 'Demo User',    'ROLE_USER',     0, 10737418240, NOW() - INTERVAL '5 days',  NOW())
ON CONFLICT (email) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Initial Sample Scans (scans)
-- ---------------------------------------------------------------------
INSERT INTO scans (id, user_id, name, description, notes, input_mode, status, file_count, total_size_bytes, thumbnail_path, created_at, updated_at) VALUES
('a1111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 'Vintage Camera 3D Model', 'High-detail photogrammetry scan of a vintage 1970s SLR camera', 'Captured with 36 studio photos', 'image', 'done', 36, 154200000, 'https://res.cloudinary.com/demo/image/upload/v1/memento3d/camera_thumb.jpg', NOW() - INTERVAL '7 days', NOW() - INTERVAL '6 days'),
('a2222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'Ergonomic Office Chair', 'Full 360-degree scan for product catalog listing', 'Balanced diffuse lighting used', 'image', 'ready', 24, 98000000, 'https://res.cloudinary.com/demo/image/upload/v1/memento3d/chair_thumb.jpg', NOW() - INTERVAL '3 days', NOW() - INTERVAL '2 days'),
('a3333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444', 'Mechanical Gear Prototype', 'Initial image set for mechanical part reconstruction', 'Industrial turntable capture', 'image', 'quality_check', 16, 62000000, NULL, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('a4444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-444444444444', 'Bronze Statue Scan Draft', 'Preliminary photo set for statue digital archive', 'Outdoor lighting capture', 'image', 'draft', 10, 35000000, NULL, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. Initial Scan Images (scan_images)
-- ---------------------------------------------------------------------
INSERT INTO scan_images (id, scan_id, original_filename, storage_path, thumbnail_path, file_size_bytes, mime_type, width, height, format, sort_order, is_excluded, upload_status, needs_quality_check, created_at) VALUES
('b1111111-1111-1111-1111-111111111101', 'a1111111-1111-1111-1111-111111111111', 'camera_01.jpg', 'uploads/33333333-3333-3333-3333-333333333333/a1111111/camera_01.jpg', 'https://res.cloudinary.com/demo/image/upload/v1/memento3d/camera_01_thumb.jpg', 4280000, 'image/jpeg', 3840, 2160, 'jpg', 1, FALSE, 'uploaded', FALSE, NOW() - INTERVAL '7 days'),
('b1111111-1111-1111-1111-111111111102', 'a1111111-1111-1111-1111-111111111111', 'camera_02.jpg', 'uploads/33333333-3333-3333-3333-333333333333/a1111111/camera_02.jpg', 'https://res.cloudinary.com/demo/image/upload/v1/memento3d/camera_02_thumb.jpg', 4310000, 'image/jpeg', 3840, 2160, 'jpg', 2, FALSE, 'uploaded', FALSE, NOW() - INTERVAL '7 days'),
('b2222222-2222-2222-2222-222222222201', 'a2222222-2222-2222-2222-222222222222', 'chair_01.jpg',  'uploads/33333333-3333-3333-3333-333333333333/a2222222/chair_01.jpg',  'https://res.cloudinary.com/demo/image/upload/v1/memento3d/chair_01_thumb.jpg',  4080000, 'image/jpeg', 3840, 2160, 'jpg', 1, FALSE, 'uploaded', FALSE, NOW() - INTERVAL '3 days'),
('b2222222-2222-2222-2222-222222222202', 'a2222222-2222-2222-2222-222222222222', 'chair_02.jpg',  'uploads/33333333-3333-3333-3333-333333333333/a2222222/chair_02.jpg',  'https://res.cloudinary.com/demo/image/upload/v1/memento3d/chair_02_thumb.jpg',  4120000, 'image/jpeg', 3840, 2160, 'jpg', 2, FALSE, 'uploaded', FALSE, NOW() - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 5. Initial Quality Check Runs (quality_check_runs)
-- ---------------------------------------------------------------------
INSERT INTO quality_check_runs (id, scan_id, run_number, algo_version, status, total_images, passed_images, failed_images, overall_score, score_category, processing_time_ms, started_at, completed_at, created_at) VALUES
('q1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', 1, 'v1.2.0', 'completed', 36, 36, 0, 92.50, 'green', 12500, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
('q2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222', 1, 'v1.2.0', 'completed', 24, 23, 1, 85.00, 'green', 9800,  NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 6. Initial Image Quality Results (image_quality_results)
-- ---------------------------------------------------------------------
INSERT INTO image_quality_results (id, quality_check_run_id, scan_image_id, sharpness_score, is_blurry, mean_brightness, is_too_dark, is_too_bright, feature_count, is_low_features, overall_status, created_at) VALUES
('r1111111-1111-1111-1111-111111111101', 'q1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111101', 185.40, FALSE, 128.50, FALSE, FALSE, 1450, FALSE, 'pass', NOW() - INTERVAL '6 days'),
('r1111111-1111-1111-1111-111111111102', 'q1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111102', 192.10, FALSE, 130.20, FALSE, FALSE, 1520, FALSE, 'pass', NOW() - INTERVAL '6 days'),
('r2222222-2222-2222-2222-222222222201', 'q2222222-2222-2222-2222-222222222222', 'b2222222-2222-2222-2222-222222222201', 142.30, FALSE, 115.80, FALSE, FALSE, 980,  FALSE, 'pass', NOW() - INTERVAL '2 days'),
('r2222222-2222-2222-2222-222222222202', 'q2222222-2222-2222-2222-222222222222', 'b2222222-2222-2222-2222-222222222202', 158.70, FALSE, 118.00, FALSE, FALSE, 1050, FALSE, 'pass', NOW() - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 7. Initial Model Ratings (model_ratings)
-- ---------------------------------------------------------------------
INSERT INTO model_ratings (id, user_id, scan_id, rating, comment, created_at) VALUES
('m1111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 'a1111111-1111-1111-1111-111111111111', 5, 'Excellent mesh detail and crisp texture resolution!', NOW() - INTERVAL '5 days'),
('m2222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444', 'a2222222-2222-2222-2222-222222222222', 4, 'Very good geometry model, smooth reconstruction process.', NOW() - INTERVAL '1 day')
ON CONFLICT (user_id, scan_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 8. Initial Daily Stats (daily_stats)
-- ---------------------------------------------------------------------
INSERT INTO daily_stats (id, stat_date, total_jobs, success_count, fail_count, success_rate_pct, total_scans_created, total_downloads, total_orders, total_revenue, active_users, created_at, updated_at) VALUES
('d1111111-1111-1111-1111-111111111111', CURRENT_DATE - INTERVAL '1 day', 15, 14, 1, 93.33, 20, 45, 3, 450000.00, 12, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
('d2222222-2222-2222-2222-222222222222', CURRENT_DATE,                   8,  8,  0, 100.00, 10, 22, 1, 150000.00, 8,  NOW(), NOW())
ON CONFLICT (stat_date) DO NOTHING;

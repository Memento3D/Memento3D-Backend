--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6 (4e955f5)
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: blacklisted_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blacklisted_tokens (
    id uuid NOT NULL,
    expiry_date timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone NOT NULL,
    token character varying(1024) NOT NULL
);


--
-- Name: image_quality_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.image_quality_results (
    id uuid NOT NULL,
    aspect_ratio_warning boolean NOT NULL,
    blur_type character varying(20),
    created_at timestamp(6) with time zone NOT NULL,
    duplicate_group_id uuid,
    failure_reasons character varying(255)[],
    feature_count integer,
    has_exposure_warning boolean NOT NULL,
    has_insufficient_overlap boolean NOT NULL,
    has_watermark_warning boolean NOT NULL,
    is_blurry boolean NOT NULL,
    is_exact_duplicate boolean NOT NULL,
    is_high_noise boolean NOT NULL,
    is_low_features boolean NOT NULL,
    is_near_duplicate boolean NOT NULL,
    is_too_bright boolean NOT NULL,
    is_too_dark boolean NOT NULL,
    mean_brightness numeric(6,2),
    overall_status character varying(10) NOT NULL,
    overlap_score_next numeric(5,2),
    overlap_score_prev numeric(5,2),
    saturated_pixel_pct numeric(5,2),
    sharpness_score numeric(10,2),
    snr_score numeric(8,2),
    watermark_area_pct numeric(5,2),
    duplicate_of_image_id uuid,
    quality_check_run_id uuid NOT NULL,
    scan_image_id uuid NOT NULL
);


--
-- Name: model_ratings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.model_ratings (
    id uuid NOT NULL,
    comment text,
    created_at timestamp(6) with time zone NOT NULL,
    rating integer NOT NULL,
    scan_id uuid NOT NULL,
    user_id uuid
);


--
-- Name: quality_check_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality_check_runs (
    id uuid NOT NULL,
    algo_version character varying(255) NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    error_message character varying(255),
    failed_images integer NOT NULL,
    overall_score numeric(38,2),
    passed_images integer NOT NULL,
    processing_time_ms integer,
    run_number integer NOT NULL,
    score_category character varying(10),
    started_at timestamp(6) with time zone NOT NULL,
    status character varying(20) NOT NULL,
    total_images integer NOT NULL,
    scan_id uuid NOT NULL,
    overridden_at timestamp(6) with time zone,
    overridden_by uuid
);


--
-- Name: reconstruction_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reconstruction_jobs (
    id uuid NOT NULL,
    attempt_id uuid,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    error_code character varying(100),
    error_message text,
    execution_mode character varying(10) NOT NULL,
    input_image_id uuid NOT NULL,
    input_snapshot jsonb NOT NULL,
    last_heartbeat_at timestamp(6) with time zone,
    profile character varying(100) NOT NULL,
    queued_at timestamp(6) with time zone NOT NULL,
    retry_count integer NOT NULL,
    stage character varying(30),
    started_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    toolchain_config jsonb NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    worker_id character varying(100),
    scan_id uuid NOT NULL,
    CONSTRAINT reconstruction_jobs_execution_mode_check CHECK (((execution_mode)::text = ANY ((ARRAY['real'::character varying, 'mock'::character varying])::text[]))),
    CONSTRAINT reconstruction_jobs_stage_check CHECK (((stage)::text = ANY ((ARRAY['downloading'::character varying, 'preprocessing'::character varying, 'shape_generation'::character varying, 'texturing'::character varying, 'validating'::character varying, 'exporting'::character varying, 'uploading'::character varying])::text[]))),
    CONSTRAINT reconstruction_jobs_status_check CHECK (((status)::text = ANY ((ARRAY['queued'::character varying, 'running'::character varying, 'done'::character varying, 'failed'::character varying, 'cancelled'::character varying])::text[])))
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked boolean NOT NULL,
    token character varying(255) NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: scan_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scan_images (
    id uuid NOT NULL,
    cloudinary_asset_id character varying(255),
    cloudinary_public_id character varying(500),
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    exclude_reason character varying(100),
    exif_data jsonb,
    file_size_bytes bigint NOT NULL,
    format character varying(10) NOT NULL,
    hash_md5 character varying(32),
    hash_perceptual character varying(64),
    height integer,
    is_excluded boolean NOT NULL,
    mime_type character varying(50) NOT NULL,
    needs_quality_check boolean NOT NULL,
    original_filename character varying(255) NOT NULL,
    sort_order integer NOT NULL,
    storage_path character varying(500) NOT NULL,
    thumbnail_path character varying(500),
    upload_status character varying(20) NOT NULL,
    width integer,
    scan_id uuid NOT NULL,
    CONSTRAINT scan_images_upload_status_check CHECK (((upload_status)::text = ANY ((ARRAY['pending'::character varying, 'uploaded'::character varying, 'failed'::character varying])::text[])))
);


--
-- Name: scan_videos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scan_videos (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    duration_seconds numeric(10,2),
    file_size_bytes bigint NOT NULL,
    format character varying(10) NOT NULL,
    fps numeric(6,2),
    frames_extracted integer,
    height integer,
    mime_type character varying(50) NOT NULL,
    original_filename character varying(255) NOT NULL,
    storage_path character varying(500) NOT NULL,
    thumbnail_path character varying(500),
    width integer,
    scan_id uuid NOT NULL
);


--
-- Name: scans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scans (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    description text,
    device_info jsonb,
    duplicated_from uuid,
    file_count integer DEFAULT 0 NOT NULL,
    input_mode character varying(10) NOT NULL,
    name character varying(255) NOT NULL,
    notes text,
    status character varying(20) NOT NULL,
    thumbnail_path character varying(500),
    total_size_bytes bigint DEFAULT 0 NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT scans_input_mode_check CHECK (((input_mode)::text = ANY ((ARRAY['image'::character varying, 'video'::character varying])::text[]))),
    CONSTRAINT scans_status_check CHECK (((status)::text = ANY ((ARRAY['draft'::character varying, 'uploading'::character varying, 'uploaded'::character varying, 'quality_check'::character varying, 'ready'::character varying, 'processing'::character varying, 'done'::character varying, 'failed'::character varying, 'deleted'::character varying])::text[])))
);


--
-- Name: upload_chunks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upload_chunks (
    id uuid NOT NULL,
    chunk_index integer NOT NULL,
    chunk_size_bytes bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    file_reference character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    storage_path character varying(500),
    uploaded_at timestamp(6) with time zone,
    scan_id uuid NOT NULL,
    CONSTRAINT upload_chunks_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'uploaded'::character varying, 'failed'::character varying, 'cancelled'::character varying])::text[])))
);


--
-- Name: upload_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upload_sessions (
    id uuid NOT NULL,
    chunk_total integer NOT NULL,
    chunk_uploaded integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expected_checksum character varying(64),
    original_filename character varying(255) NOT NULL,
    target_id uuid,
    target_type character varying(50) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    upload_status character varying(20) NOT NULL,
    uploaded_chunks_bitmap text
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(150) NOT NULL,
    full_name character varying(100) NOT NULL,
    password character varying(255) NOT NULL,
    role character varying(20) NOT NULL,
    storage_quota_bytes bigint DEFAULT '10737418240'::bigint NOT NULL,
    storage_used_bytes bigint DEFAULT 0 NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['ROLE_USER'::character varying, 'ROLE_ADMIN'::character varying, 'ROLE_OPERATOR'::character varying])::text[])))
);


--
-- Name: blacklisted_tokens blacklisted_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blacklisted_tokens
    ADD CONSTRAINT blacklisted_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens idx_refresh_token_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT idx_refresh_token_token UNIQUE (token);


--
-- Name: image_quality_results image_quality_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_quality_results
    ADD CONSTRAINT image_quality_results_pkey PRIMARY KEY (id);


--
-- Name: model_ratings model_ratings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.model_ratings
    ADD CONSTRAINT model_ratings_pkey PRIMARY KEY (id);


--
-- Name: quality_check_runs quality_check_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_check_runs
    ADD CONSTRAINT quality_check_runs_pkey PRIMARY KEY (id);


--
-- Name: reconstruction_jobs reconstruction_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reconstruction_jobs
    ADD CONSTRAINT reconstruction_jobs_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: scan_images scan_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scan_images
    ADD CONSTRAINT scan_images_pkey PRIMARY KEY (id);


--
-- Name: scan_videos scan_videos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scan_videos
    ADD CONSTRAINT scan_videos_pkey PRIMARY KEY (id);


--
-- Name: scans scans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scans
    ADD CONSTRAINT scans_pkey PRIMARY KEY (id);


--
-- Name: users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: blacklisted_tokens ukibvoggbe8ijw4l7xyyotp5n7g; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blacklisted_tokens
    ADD CONSTRAINT ukibvoggbe8ijw4l7xyyotp5n7g UNIQUE (token);


--
-- Name: upload_chunks upload_chunks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_chunks
    ADD CONSTRAINT upload_chunks_pkey PRIMARY KEY (id);


--
-- Name: upload_sessions upload_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_sessions
    ADD CONSTRAINT upload_sessions_pkey PRIMARY KEY (id);


--
-- Name: upload_chunks uq_upload_chunks_scan_ref_idx; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_chunks
    ADD CONSTRAINT uq_upload_chunks_scan_ref_idx UNIQUE (scan_id, file_reference, chunk_index);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_blacklisted_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blacklisted_token ON public.blacklisted_tokens USING btree (token);


--
-- Name: idx_iqr_image; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iqr_image ON public.image_quality_results USING btree (scan_image_id);


--
-- Name: idx_iqr_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iqr_run ON public.image_quality_results USING btree (quality_check_run_id);


--
-- Name: idx_iqr_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iqr_status ON public.image_quality_results USING btree (overall_status);


--
-- Name: idx_ratings_scan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ratings_scan ON public.model_ratings USING btree (scan_id);


--
-- Name: idx_reconstruction_jobs_scan_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reconstruction_jobs_scan_created ON public.reconstruction_jobs USING btree (scan_id, created_at);


--
-- Name: idx_reconstruction_jobs_status_queued; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reconstruction_jobs_status_queued ON public.reconstruction_jobs USING btree (status, queued_at);


--
-- Name: idx_refresh_token_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_user_id ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_scan_images_excluded; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scan_images_excluded ON public.scan_images USING btree (scan_id, is_excluded);


--
-- Name: idx_scan_images_qc_flag; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scan_images_qc_flag ON public.scan_images USING btree (scan_id, needs_quality_check);


--
-- Name: idx_scan_images_scan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scan_images_scan ON public.scan_images USING btree (scan_id);


--
-- Name: idx_scans_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scans_created ON public.scans USING btree (user_id, created_at DESC);


--
-- Name: idx_scans_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scans_status ON public.scans USING btree (status);


--
-- Name: idx_scans_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scans_user ON public.scans USING btree (user_id);


--
-- Name: idx_scans_user_status_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_scans_user_status_date ON public.scans USING btree (user_id, status, created_at DESC);


--
-- Name: idx_upload_chunks_scan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_upload_chunks_scan ON public.upload_chunks USING btree (scan_id, file_reference);


--
-- Name: refresh_tokens fk1lih5y2npsf8u5o3vhdb9y0os; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: quality_check_runs fk2swajg00mx0h1hhxfc3ywek3i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_check_runs
    ADD CONSTRAINT fk2swajg00mx0h1hhxfc3ywek3i FOREIGN KEY (scan_id) REFERENCES public.scans(id);


--
-- Name: image_quality_results fk54nupwptm87jeouqicbgio8q6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_quality_results
    ADD CONSTRAINT fk54nupwptm87jeouqicbgio8q6 FOREIGN KEY (quality_check_run_id) REFERENCES public.quality_check_runs(id);


--
-- Name: reconstruction_jobs fk7jdqe3mb53uscutc7xvik4iwb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reconstruction_jobs
    ADD CONSTRAINT fk7jdqe3mb53uscutc7xvik4iwb FOREIGN KEY (scan_id) REFERENCES public.scans(id);


--
-- Name: scan_images fkbikfpsfvb8s7dptub3amhpfwi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scan_images
    ADD CONSTRAINT fkbikfpsfvb8s7dptub3amhpfwi FOREIGN KEY (scan_id) REFERENCES public.scans(id);


--
-- Name: upload_chunks fkcyw1h6cquc902kb0sr59t97q9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_chunks
    ADD CONSTRAINT fkcyw1h6cquc902kb0sr59t97q9 FOREIGN KEY (scan_id) REFERENCES public.scans(id);


--
-- Name: model_ratings fkd240q4ktx8cy9vlxwvxkdj604; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.model_ratings
    ADD CONSTRAINT fkd240q4ktx8cy9vlxwvxkdj604 FOREIGN KEY (scan_id) REFERENCES public.scans(id);


--
-- Name: image_quality_results fke0raulbun0cv87id88xsy5088; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_quality_results
    ADD CONSTRAINT fke0raulbun0cv87id88xsy5088 FOREIGN KEY (duplicate_of_image_id) REFERENCES public.scan_images(id);


--
-- Name: image_quality_results fkflrgn1g3nqde8mu5bnoh8jrgb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_quality_results
    ADD CONSTRAINT fkflrgn1g3nqde8mu5bnoh8jrgb FOREIGN KEY (scan_image_id) REFERENCES public.scan_images(id);


--
-- Name: model_ratings fknpcx3qf94r1dd2lr1e61ptx1d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.model_ratings
    ADD CONSTRAINT fknpcx3qf94r1dd2lr1e61ptx1d FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: scans fkr1cg078mdlk9vlcwx1cieb8at; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scans
    ADD CONSTRAINT fkr1cg078mdlk9vlcwx1cieb8at FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: scan_videos fkrnw9yqcq338ywpbjl038rjgga; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scan_videos
    ADD CONSTRAINT fkrnw9yqcq338ywpbjl038rjgga FOREIGN KEY (scan_id) REFERENCES public.scans(id);


--
-- PostgreSQL database dump complete
--



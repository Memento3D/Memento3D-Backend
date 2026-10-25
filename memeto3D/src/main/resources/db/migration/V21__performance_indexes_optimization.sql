-- V21: Performance Optimization Indexes
-- Adds missing Foreign Key and Composite Indexes to eliminate table scans and in-memory sorting

-- 1. Print Jobs: Foreign key lookup by order_id and status/created_at compound indexing
CREATE INDEX IF NOT EXISTS idx_print_jobs_order_id ON public.print_jobs (order_id);
CREATE INDEX IF NOT EXISTS idx_print_jobs_status_created ON public.print_jobs (status, created_at DESC);

-- 2. Order Items: Foreign key lookup by product_id
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON public.order_items (product_id);

-- 3. Print Orders: Composite index for user order history pagination and status filtering
CREATE INDEX IF NOT EXISTS idx_print_orders_user_created ON public.print_orders (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_print_orders_status_created ON public.print_orders (status, created_at DESC);

-- 4. Notifications: Composite index for unread badge count and chronological feed
CREATE INDEX IF NOT EXISTS idx_notifications_user_read_created ON public.notifications (user_id, is_read, created_at DESC);

-- 5. Physical Quality Checks: Composite index for fast QC history and status check
CREATE INDEX IF NOT EXISTS idx_physical_quality_checks_job_created ON public.physical_quality_checks (print_job_id, created_at DESC);

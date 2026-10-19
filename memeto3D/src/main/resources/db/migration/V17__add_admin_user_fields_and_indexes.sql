-- ============================================================================
-- Migration: V17__add_admin_user_fields_and_indexes.sql
-- Description: Add phone, last_login_at to users table, ensure user status
--              includes INACTIVE/LOCKED/DELETED, and create optimized indexes for
--              Admin Dashboard, Users, Orders, Payments, and Audit Logs.
-- ============================================================================

-- 1. Add phone and last_login_at to users table
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- 2. Indexes on users table for fast Admin filtering, sorting, and counts
CREATE INDEX IF NOT EXISTS idx_users_role ON public.users (role);
CREATE INDEX IF NOT EXISTS idx_users_status ON public.users (status);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON public.users (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_phone ON public.users (phone);
CREATE INDEX IF NOT EXISTS idx_users_last_login_at ON public.users (last_login_at DESC);

-- 3. Indexes on orders (print_orders) table for Dashboard aggregation and order list
CREATE INDEX IF NOT EXISTS idx_print_orders_status ON public.print_orders (status);
CREATE INDEX IF NOT EXISTS idx_print_orders_created_at ON public.print_orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_print_orders_user_id ON public.print_orders (user_id);
CREATE INDEX IF NOT EXISTS idx_print_orders_payment_status ON public.print_orders (payment_status);

-- 4. Indexes on payment_transactions table for financial reporting
CREATE INDEX IF NOT EXISTS idx_payment_transactions_status ON public.payment_transactions (status);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_created_at ON public.payment_transactions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_payment_method ON public.payment_transactions (payment_method);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_order_id ON public.payment_transactions (order_id);

-- 5. Indexes on audit_logs table for fast filtering
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON public.audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON public.audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_id ON public.audit_logs (actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type ON public.audit_logs (entity_type);

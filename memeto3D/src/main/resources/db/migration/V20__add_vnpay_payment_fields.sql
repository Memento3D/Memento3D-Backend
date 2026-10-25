-- ==============================================================================
-- MIGRATION V20: ADD VNPAY & EXTENDED GATEWAY AUDIT FIELDS TO PAYMENT TRANSACTIONS
-- ==============================================================================
-- Extends payment_transactions with response_code, gateway_status, bank_code, card_type
-- and adds performance indexes on merchant_transaction_id and provider_transaction_id.

ALTER TABLE public.payment_transactions
    ADD COLUMN IF NOT EXISTS response_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS gateway_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS card_type VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_ref
    ON public.payment_transactions (merchant_transaction_id);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_provider_tx
    ON public.payment_transactions (provider_transaction_id);

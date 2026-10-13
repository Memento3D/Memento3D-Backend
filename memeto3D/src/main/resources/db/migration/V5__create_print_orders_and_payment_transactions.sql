CREATE TABLE public.print_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    scan_id UUID,
    total_price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    recipient_name VARCHAR(255),
    shipping_address TEXT,
    phone VARCHAR(20),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_print_orders_user
        FOREIGN KEY (user_id)
            REFERENCES public.users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_print_orders_scan
        FOREIGN KEY (scan_id)
            REFERENCES public.scans (id)
            ON DELETE SET NULL
);

CREATE INDEX idx_print_orders_user
    ON public.print_orders (user_id);

CREATE INDEX idx_print_orders_status
    ON public.print_orders (status);

CREATE TABLE public.payment_transactions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id UUID NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    merchant_transaction_id VARCHAR(100) NOT NULL UNIQUE,
    provider_transaction_id VARCHAR(255),
    request_id VARCHAR(100) NOT NULL,
    payment_url TEXT,
    failure_code VARCHAR(50),
    failure_message TEXT,
    paid_at TIMESTAMP WITH TIME ZONE,
    expired_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_payment_transactions_order
        FOREIGN KEY (order_id)
            REFERENCES public.print_orders (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_payment_transactions_user
        FOREIGN KEY (user_id)
            REFERENCES public.users (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_payment_transactions_order
    ON public.payment_transactions (order_id);

CREATE INDEX idx_payment_transactions_user
    ON public.payment_transactions (user_id);

CREATE INDEX idx_payment_transactions_status
    ON public.payment_transactions (status);

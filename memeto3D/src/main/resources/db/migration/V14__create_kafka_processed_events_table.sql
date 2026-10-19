-- V14: Create processed_events table for Kafka Consumer Idempotency
CREATE TABLE IF NOT EXISTS public.processed_events (
    event_id VARCHAR(100) PRIMARY KEY,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_events_time ON public.processed_events (processed_at);

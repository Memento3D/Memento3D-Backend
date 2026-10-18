-- Phase 1: Printer + PrinterAgent
-- Telemetry (temperatures/progress) is intentionally NOT stored here; see Phase 8.

CREATE TABLE public.printer_agents (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    agent_version VARCHAR(50),
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    token_rotated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_printer_agents_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_printer_agents_status CHECK (status IN ('ONLINE', 'OFFLINE', 'REVOKED')),
    CONSTRAINT fk_printer_agents_created_by
        FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_printer_agents_name_lower
    ON public.printer_agents (LOWER(name));

CREATE INDEX idx_printer_agents_status_heartbeat
    ON public.printer_agents (status, last_heartbeat_at);

CREATE TABLE public.printers (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    provider VARCHAR(30) NOT NULL DEFAULT 'OCTOPRINT',
    model VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    agent_id UUID,
    octoprint_printer_id VARCHAR(100),
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_printers_status
        CHECK (status IN ('OFFLINE', 'IDLE', 'PREPARING', 'PRINTING', 'PAUSED', 'ERROR', 'MAINTENANCE')),
    CONSTRAINT chk_printers_provider CHECK (provider IN ('OCTOPRINT', 'OTHER')),
    -- agent and its remote id are set together or not at all
    CONSTRAINT chk_printers_agent_mapping
        CHECK ((agent_id IS NULL) = (octoprint_printer_id IS NULL)),
    CONSTRAINT uq_printers_agent_remote UNIQUE (agent_id, octoprint_printer_id),
    CONSTRAINT fk_printers_agent
        FOREIGN KEY (agent_id) REFERENCES public.printer_agents (id) ON DELETE RESTRICT,
    CONSTRAINT fk_printers_created_by
        FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_printers_serial_lower
    ON public.printers (LOWER(serial_number));

CREATE INDEX idx_printers_status ON public.printers (status);
CREATE INDEX idx_printers_agent ON public.printers (agent_id);

package com.memento3d.printers.application.service;

import com.memento3d.printers.api.request.AgentHeartbeatRequest;
import com.memento3d.printers.api.request.RegisterPrinterAgentRequest;
import com.memento3d.printers.api.response.AgentHeartbeatResponse;
import com.memento3d.printers.api.response.PrinterAgentCredentialResponse;
import com.memento3d.printers.api.response.PrinterAgentResponse;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.UUID;

public interface PrinterAgentService {

    PrinterAgentCredentialResponse register(RegisterPrinterAgentRequest request, UUID actorId);

    Page<PrinterAgentResponse> list(int page, int limit);

    PrinterAgentResponse get(UUID id);

    PrinterAgentCredentialResponse rotateToken(UUID id);

    PrinterAgentResponse revoke(UUID id);

    /** Agent-facing: authenticates by token and syncs agent + printer state. */
    AgentHeartbeatResponse heartbeat(String token, AgentHeartbeatRequest request);

    /** Scheduler-facing: marks agents silent since {@code threshold} (and their printers) OFFLINE. */
    int markStaleAgentsOffline(Instant threshold);
}

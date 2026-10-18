package com.memento3d.printers.api;

import com.memento3d.printers.api.request.AgentHeartbeatRequest;
import com.memento3d.printers.api.response.AgentHeartbeatResponse;
import com.memento3d.printers.application.service.PrinterAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Machine-to-machine endpoints for Local Printer Agents. These are NOT JWT protected
 * (SecurityConfig permits the path); authentication is the {@code X-Agent-Token} header,
 * verified by {@link PrinterAgentService#heartbeat}.
 */
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Printer Agent Gateway", description = "Endpoints called by Local Printer Agents (token auth)")
public class AgentGatewayController {

    private final PrinterAgentService agentService;

    @PostMapping("/heartbeat")
    @Operation(summary = "Agent heartbeat: report agent liveness and printer states")
    public ResponseEntity<AgentHeartbeatResponse> heartbeat(
            @RequestHeader(name = "X-Agent-Token", required = false) String token,
            @Valid @RequestBody AgentHeartbeatRequest request) {
        return ResponseEntity.ok(agentService.heartbeat(token, request));
    }
}

package com.memento3d.printers.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.printers.api.request.RegisterPrinterAgentRequest;
import com.memento3d.printers.api.response.PrinterAgentCredentialResponse;
import com.memento3d.printers.api.response.PrinterAgentResponse;
import com.memento3d.printers.application.service.PrinterAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/printer-agents")
@RequiredArgsConstructor
@Tag(name = "Printer Agents", description = "Register and manage Local Printer Agents")
@SecurityRequirement(name = "BearerAuth")
public class PrinterAgentController {

    private final PrinterAgentService agentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "List printer agents")
    public ResponseEntity<Page<PrinterAgentResponse>> list(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(agentService.list(page, limit));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Get printer agent")
    public ResponseEntity<PrinterAgentResponse> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(agentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register an agent and receive its one-time token (ADMIN)")
    public ResponseEntity<PrinterAgentCredentialResponse> register(
            @Valid @RequestBody RegisterPrinterAgentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        PrinterAgentCredentialResponse response = agentService.register(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/printer-agents/" + response.getAgent().getId()))
                .body(response);
    }

    @PostMapping("/{id}/token-rotations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rotate the agent token (ADMIN); old token stops working immediately")
    public ResponseEntity<PrinterAgentCredentialResponse> rotate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(agentService.rotateToken(id));
    }

    @PostMapping("/{id}/revocations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Permanently revoke an agent (ADMIN)")
    public ResponseEntity<PrinterAgentResponse> revoke(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(agentService.revoke(id));
    }
}

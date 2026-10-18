package com.memento3d.printers.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.printers.api.request.CreatePrinterRequest;
import com.memento3d.printers.api.response.PrinterResponse;
import com.memento3d.printers.application.service.PrinterService;
import com.memento3d.printers.domain.model.PrinterStatus;
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

/**
 * Printer management. Reads are open to MANAGER (ROLE_OPERATOR) and ADMIN;
 * registration is ADMIN only. Authorization is enforced server-side via @PreAuthorize.
 */
@RestController
@RequestMapping("/api/v1/printers")
@RequiredArgsConstructor
@Tag(name = "Printers", description = "Printer inventory and status for managers")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
public class PrinterController {

    private final PrinterService printerService;

    @GetMapping
    @Operation(summary = "List printers (paginated, optional status filter)")
    public ResponseEntity<Page<PrinterResponse>> list(
            @RequestParam(name = "status", required = false) PrinterStatus status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(printerService.list(status, page, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get printer by id")
    public ResponseEntity<PrinterResponse> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(printerService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Register a printer (ADMIN)")
    public ResponseEntity<PrinterResponse> create(
            @Valid @RequestBody CreatePrinterRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        PrinterResponse response = printerService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/printers/" + response.getId()))
                .body(response);
    }

    @PostMapping("/{id}/maintenance")
    @Operation(summary = "Put an idle/offline/error printer into maintenance")
    public ResponseEntity<PrinterResponse> enterMaintenance(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(printerService.enterMaintenance(id));
    }

    @PostMapping("/{id}/maintenance-end")
    @Operation(summary = "Take a printer out of maintenance")
    public ResponseEntity<PrinterResponse> exitMaintenance(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(printerService.exitMaintenance(id));
    }
}

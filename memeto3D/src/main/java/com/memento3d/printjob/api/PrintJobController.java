package com.memento3d.printjob.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.printjob.api.request.ConsumptionReconciliationRequest;
import com.memento3d.printjob.api.request.CreatePrintJobRequest;
import com.memento3d.printjob.api.request.MaterialIssueRequest;
import com.memento3d.printjob.api.request.ReserveStockRequest;
import com.memento3d.printjob.api.response.PrintJobConsumptionResponse;
import com.memento3d.printjob.api.response.PrintJobResponse;
import com.memento3d.printjob.api.response.StockReservationResponse;
import com.memento3d.printjob.application.service.PrintJobService;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/print-jobs")
@RequiredArgsConstructor
@Tag(name = "Print Jobs & Material Consumption", description = "Print Job lifecycle, reservation, issue, and consumption reconciliation (F06, F07)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class PrintJobController {

    private final PrintJobService printJobService;

    @PostMapping
    @Operation(summary = "Create a new Print Job")
    public ResponseEntity<PrintJobResponse> createJob(
            @Valid @RequestBody CreatePrintJobRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        PrintJobResponse response = printJobService.createPrintJob(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/print-jobs/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List Print Jobs with pagination")
    public ResponseEntity<Page<PrintJobResponse>> listJobs(
            @RequestParam(name = "status", required = false) PrintJobStatus status,
            @RequestParam(name = "printerId", required = false) UUID printerId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit);
        return ResponseEntity.ok(printJobService.listPrintJobs(status, printerId, materialId, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Print Job details")
    public ResponseEntity<PrintJobResponse> getJob(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(printJobService.getPrintJob(id));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start print job execution on printer")
    public ResponseEntity<PrintJobResponse> startPrint(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(printJobService.startPrint(id));
    }

    @PostMapping("/{id}/reservations")
    @Operation(summary = "Reserve material from warehouse for Print Job (F06)")
    public ResponseEntity<StockReservationResponse> reserveStock(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReserveStockRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        StockReservationResponse response = printJobService.reserveStock(id, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/reservations")
    @Operation(summary = "Get active stock reservation for Print Job")
    public ResponseEntity<StockReservationResponse> getReservation(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(printJobService.getReservation(id));
    }

    @DeleteMapping("/{id}/reservations")
    @Operation(summary = "Release reserved material back to available stock")
    public ResponseEntity<Void> releaseReservation(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        printJobService.releaseReservation(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/material-issues")
    @Operation(summary = "Issue material to printer for Print Job (F07)")
    public ResponseEntity<PrintJobConsumptionResponse> issueMaterial(
            @PathVariable("id") UUID id,
            @Valid @RequestBody MaterialIssueRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(printJobService.issueMaterial(id, request, actorId));
    }

    @PostMapping("/{id}/consumption-reconciliations")
    @Operation(summary = "Reconcile consumption after printing: issued = used + waste + returned (F07)")
    public ResponseEntity<PrintJobConsumptionResponse> reconcile(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ConsumptionReconciliationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(printJobService.reconcileConsumption(id, request, actorId));
    }

    @GetMapping("/{id}/material-consumption")
    @Operation(summary = "Get material consumption details for Print Job")
    public ResponseEntity<PrintJobConsumptionResponse> getConsumption(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(printJobService.getConsumption(id));
    }
}

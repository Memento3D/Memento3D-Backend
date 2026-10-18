package com.memento3d.inventory.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.request.CreateStockAdjustmentRequest;
import com.memento3d.inventory.api.response.StockAdjustmentResponse;
import com.memento3d.inventory.application.service.StockAdjustmentService;
import com.memento3d.inventory.domain.model.StockAdjustmentStatus;
import com.memento3d.warehouse.application.service.WarehouseSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-adjustments")
@RequiredArgsConstructor
@Tag(name = "Stock Adjustments", description = "Discrepancy adjustment request and approval workflow")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class StockAdjustmentController {

    private final StockAdjustmentService stockAdjustmentService;
    private final WarehouseSecurityService warehouseSecurityService;

    @PostMapping
    @Operation(summary = "Create a stock adjustment request")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<StockAdjustmentResponse> create(
            @Valid @RequestBody CreateStockAdjustmentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(request.getWarehouseId(), actorId, role);

        StockAdjustmentResponse response = stockAdjustmentService.createAdjustment(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/stock-adjustments/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List stock adjustments with filters")
    public ResponseEntity<Page<StockAdjustmentResponse>> list(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "status", required = false) StockAdjustmentStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        if (warehouseId != null) {
            warehouseSecurityService.validateWarehouseAccess(warehouseId, actorId, role);
        }

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(stockAdjustmentService.listAdjustments(warehouseId, materialId, status, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get adjustment details by ID")
    public ResponseEntity<StockAdjustmentResponse> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(stockAdjustmentService.getAdjustment(id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit adjustment for manager review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<StockAdjustmentResponse> submit(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockAdjustmentService.submitAdjustment(id, actorId));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve stock adjustment (Manager or Admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<StockAdjustmentResponse> approve(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockAdjustmentService.approveAdjustment(id, actorId));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject stock adjustment (Manager or Admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<StockAdjustmentResponse> reject(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String reason = body != null ? body.getOrDefault("reason", "Disapproved by manager") : "Disapproved by manager";
        return ResponseEntity.ok(stockAdjustmentService.rejectAdjustment(id, reason, actorId));
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply approved stock adjustment to live inventory")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<StockAdjustmentResponse> apply(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockAdjustmentService.applyAdjustment(id, actorId));
    }
}

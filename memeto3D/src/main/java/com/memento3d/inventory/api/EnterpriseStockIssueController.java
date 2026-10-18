package com.memento3d.inventory.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.request.CreateEnterpriseStockIssueRequest;
import com.memento3d.inventory.api.response.EnterpriseStockIssueResponse;
import com.memento3d.inventory.application.service.EnterpriseStockIssueService;
import com.memento3d.inventory.domain.model.EnterpriseStockIssueStatus;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-issues")
@RequiredArgsConstructor
@Tag(name = "Stock Issues", description = "Enterprise Stock Issues workflow (DRAFT -> SUBMITTED -> APPROVED -> COMPLETED)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class EnterpriseStockIssueController {

    private final EnterpriseStockIssueService stockIssueService;
    private final WarehouseSecurityService warehouseSecurityService;

    @PostMapping
    @Operation(summary = "Create a stock issue draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<EnterpriseStockIssueResponse> create(
            @Valid @RequestBody CreateEnterpriseStockIssueRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(request.getWarehouseId(), actorId, role);

        EnterpriseStockIssueResponse response = stockIssueService.createIssue(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/stock-issues/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List stock issues with warehouse and status filters")
    public ResponseEntity<Page<EnterpriseStockIssueResponse>> list(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "status", required = false) EnterpriseStockIssueStatus status,
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
        return ResponseEntity.ok(stockIssueService.listIssues(warehouseId, status, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get stock issue details by ID")
    public ResponseEntity<EnterpriseStockIssueResponse> getById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        EnterpriseStockIssueResponse response = stockIssueService.getIssue(id);
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(response.getWarehouseId(), actorId, role);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit stock issue for approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<EnterpriseStockIssueResponse> submit(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockIssueService.submitIssue(id, actorId));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve stock issue (Manager or Admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EnterpriseStockIssueResponse> approve(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockIssueService.approveIssue(id, actorId));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete stock issue and deduct inventory stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<EnterpriseStockIssueResponse> complete(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockIssueService.completeIssue(id, actorId));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel stock issue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EnterpriseStockIssueResponse> cancel(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockIssueService.cancelIssue(id, actorId));
    }
}

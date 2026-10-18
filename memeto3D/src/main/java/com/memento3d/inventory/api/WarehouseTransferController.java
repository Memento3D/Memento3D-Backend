package com.memento3d.inventory.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.request.CreateWarehouseTransferRequest;
import com.memento3d.inventory.api.response.WarehouseTransferResponse;
import com.memento3d.inventory.application.service.WarehouseTransferService;
import com.memento3d.inventory.domain.model.WarehouseTransferStatus;
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
@RequestMapping("/api/v1/warehouse-transfers")
@RequiredArgsConstructor
@Tag(name = "Warehouse Transfers", description = "Inter-warehouse stock transfer workflow (DRAFT -> SUBMITTED -> APPROVED -> IN_TRANSIT -> RECEIVED)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class WarehouseTransferController {

    private final WarehouseTransferService transferService;
    private final WarehouseSecurityService warehouseSecurityService;

    @PostMapping
    @Operation(summary = "Create warehouse transfer draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<WarehouseTransferResponse> create(
            @Valid @RequestBody CreateWarehouseTransferRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(request.getSourceWarehouseId(), actorId, role);

        WarehouseTransferResponse response = transferService.createTransfer(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/warehouse-transfers/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List transfers with warehouse and status filter")
    public ResponseEntity<Page<WarehouseTransferResponse>> list(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "status", required = false) WarehouseTransferStatus status,
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
        return ResponseEntity.ok(transferService.listTransfers(warehouseId, status, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transfer details by ID")
    public ResponseEntity<WarehouseTransferResponse> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit transfer for manager approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<WarehouseTransferResponse> submit(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(transferService.submitTransfer(id, actorId));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve warehouse transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<WarehouseTransferResponse> approve(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(transferService.approveTransfer(id, actorId));
    }

    @PostMapping("/{id}/ship")
    @Operation(summary = "Ship transfer (deducts stock from source warehouse and puts IN_TRANSIT)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<WarehouseTransferResponse> ship(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(transferService.shipTransfer(id, actorId));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Receive transfer (increments stock in destination warehouse)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<WarehouseTransferResponse> receive(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(transferService.receiveTransfer(id, actorId));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<WarehouseTransferResponse> cancel(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(transferService.cancelTransfer(id, actorId));
    }
}

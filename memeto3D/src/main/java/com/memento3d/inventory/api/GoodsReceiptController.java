package com.memento3d.inventory.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.request.CreateGoodsReceiptRequest;
import com.memento3d.inventory.api.request.UpdateGoodsReceiptRequest;
import com.memento3d.inventory.api.response.GoodsReceiptResponse;
import com.memento3d.inventory.application.service.GoodsReceiptService;
import com.memento3d.inventory.domain.model.GoodsReceiptStatus;
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
@RequestMapping("/api/v1/goods-receipts")
@RequiredArgsConstructor
@Tag(name = "Goods Receipts", description = "Enterprise Goods Receipts workflow (DRAFT -> SUBMITTED -> APPROVED -> COMPLETED)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;
    private final WarehouseSecurityService warehouseSecurityService;

    @PostMapping
    @Operation(summary = "Create a goods receipt draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<GoodsReceiptResponse> create(
            @Valid @RequestBody CreateGoodsReceiptRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(request.getWarehouseId(), actorId, role);

        GoodsReceiptResponse response = goodsReceiptService.createReceipt(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/goods-receipts/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List goods receipts with warehouse and status filters")
    public ResponseEntity<Page<GoodsReceiptResponse>> list(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "status", required = false) GoodsReceiptStatus status,
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
        return ResponseEntity.ok(goodsReceiptService.listReceipts(warehouseId, status, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get goods receipt details by ID")
    public ResponseEntity<GoodsReceiptResponse> getById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        GoodsReceiptResponse response = goodsReceiptService.getReceipt(id);
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(response.getWarehouseId(), actorId, role);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update goods receipt details (DRAFT only)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<GoodsReceiptResponse> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateGoodsReceiptRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(goodsReceiptService.updateReceipt(id, request, actorId));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit goods receipt for approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<GoodsReceiptResponse> submit(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(goodsReceiptService.submitReceipt(id, actorId));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve goods receipt (Manager or Admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<GoodsReceiptResponse> approve(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(goodsReceiptService.approveReceipt(id, actorId));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete goods receipt and increment inventory stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<GoodsReceiptResponse> complete(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(goodsReceiptService.completeReceipt(id, actorId));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel goods receipt")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<GoodsReceiptResponse> cancel(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(goodsReceiptService.cancelReceipt(id, actorId));
    }
}

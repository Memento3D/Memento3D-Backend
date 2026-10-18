package com.memento3d.inventory.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.request.CreateStockIssueRequest;
import com.memento3d.inventory.api.request.CreateStockReceiptRequest;
import com.memento3d.inventory.api.response.InventoryBalanceResponse;
import com.memento3d.inventory.api.response.InventoryTransactionResponse;
import com.memento3d.inventory.api.response.StockIssueResponse;
import com.memento3d.inventory.api.response.StockReceiptResponse;
import com.memento3d.inventory.application.service.InventoryService;
import com.memento3d.inventory.domain.model.InventoryTransactionType;
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
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory & Warehouse Operations", description = "Stock balances, goods receipt, manual issues and audit ledger (F03, F04, F05, F08)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class InventoryController {

    private final InventoryService inventoryService;
    private final WarehouseSecurityService warehouseSecurityService;

    @GetMapping("/balances")
    @Operation(summary = "Get inventory stock balances with warehouse and material filters (F03)")
    public ResponseEntity<Page<InventoryBalanceResponse>> listBalances(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        if (warehouseId != null && currentUser != null) {
            warehouseSecurityService.validateWarehouseAccess(warehouseId, currentUser.getId(), currentUser.getRole().name());
        }
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit);
        return ResponseEntity.ok(inventoryService.listBalances(warehouseId, materialId, pageRequest));
    }

    @PostMapping("/receipts")
    @Operation(summary = "Create goods receipt to increment inventory on-hand (F04)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<StockReceiptResponse> createReceipt(
            @Valid @RequestBody CreateStockReceiptRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(request.getWarehouseId(), actorId, role);

        StockReceiptResponse response = inventoryService.createReceipt(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/inventory/receipts/" + response.getId()))
                .body(response);
    }

    @GetMapping("/receipts")
    @Operation(summary = "List goods receipts with pagination")
    public ResponseEntity<Page<StockReceiptResponse>> listReceipts(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        if (warehouseId != null && currentUser != null) {
            warehouseSecurityService.validateWarehouseAccess(warehouseId, currentUser.getId(), currentUser.getRole().name());
        }
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(inventoryService.listReceipts(warehouseId, materialId, pageRequest));
    }

    @PostMapping("/issues")
    @Operation(summary = "Manually issue material from warehouse stock with required reason (F05)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<StockIssueResponse> createIssue(
            @Valid @RequestBody CreateStockIssueRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(request.getWarehouseId(), actorId, role);

        StockIssueResponse response = inventoryService.createIssue(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/inventory/issues/" + response.getId()))
                .body(response);
    }

    @GetMapping("/issues")
    @Operation(summary = "List manual stock issues with pagination")
    public ResponseEntity<Page<StockIssueResponse>> listIssues(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        if (warehouseId != null && currentUser != null) {
            warehouseSecurityService.validateWarehouseAccess(warehouseId, currentUser.getId(), currentUser.getRole().name());
        }
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(inventoryService.listIssues(warehouseId, materialId, pageRequest));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Query immutable inventory ledger transactions (F08)")
    public ResponseEntity<Page<InventoryTransactionResponse>> listTransactions(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "materialId", required = false) UUID materialId,
            @RequestParam(name = "type", required = false) InventoryTransactionType type,
            @RequestParam(name = "printJobId", required = false) UUID printJobId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        if (warehouseId != null && currentUser != null) {
            warehouseSecurityService.validateWarehouseAccess(warehouseId, currentUser.getId(), currentUser.getRole().name());
        }
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit);
        return ResponseEntity.ok(inventoryService.listTransactions(warehouseId, materialId, type, printJobId, pageRequest));
    }
}

package com.memento3d.inventory.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.request.CreateStockCountRequest;
import com.memento3d.inventory.api.request.UpdateStockCountItemsRequest;
import com.memento3d.inventory.api.response.StockCountResponse;
import com.memento3d.inventory.application.service.StockCountService;
import com.memento3d.inventory.domain.model.StockCountStatus;
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
@RequestMapping("/api/v1/stock-counts")
@RequiredArgsConstructor
@Tag(name = "Stock Counts", description = "Inventory audit & stock count physical verification workflow")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class StockCountController {

    private final StockCountService stockCountService;
    private final WarehouseSecurityService warehouseSecurityService;

    @PostMapping
    @Operation(summary = "Create a new stock count audit draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<StockCountResponse> create(
            @Valid @RequestBody CreateStockCountRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(request.getWarehouseId(), actorId, role);

        StockCountResponse response = stockCountService.createStockCount(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/stock-counts/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List stock count sessions with filters")
    public ResponseEntity<Page<StockCountResponse>> list(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @RequestParam(name = "status", required = false) StockCountStatus status,
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
        return ResponseEntity.ok(stockCountService.listStockCounts(warehouseId, status, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get stock count details by ID")
    public ResponseEntity<StockCountResponse> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(stockCountService.getStockCount(id));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start stock counting (snapshots system stock)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<StockCountResponse> start(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockCountService.startCount(id, actorId));
    }

    @PutMapping("/{id}/items")
    @Operation(summary = "Record physical counted quantities")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    public ResponseEntity<StockCountResponse> updateItems(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateStockCountItemsRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockCountService.recordCounts(id, request, actorId));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete stock count and trigger adjustment for variances")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<StockCountResponse> complete(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(stockCountService.completeCount(id, actorId));
    }
}

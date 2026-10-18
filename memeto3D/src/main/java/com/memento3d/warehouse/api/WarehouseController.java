package com.memento3d.warehouse.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.request.UpdateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.application.service.WarehouseService;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
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
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouse Management", description = "Warehouse facility management (F02)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @Operation(summary = "Register a new warehouse facility")
    public ResponseEntity<WarehouseResponse> create(
            @Valid @RequestBody CreateWarehouseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        WarehouseResponse response = warehouseService.createWarehouse(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/warehouses/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List warehouses with filters and pagination")
    public ResponseEntity<Page<WarehouseResponse>> list(
            @RequestParam(name = "status", required = false) WarehouseStatus status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit, Sort.by(Sort.Direction.ASC, "name"));
        return ResponseEntity.ok(warehouseService.listWarehouses(status, search, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get warehouse details by ID")
    public ResponseEntity<WarehouseResponse> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(warehouseService.getWarehouse(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update warehouse details")
    public ResponseEntity<WarehouseResponse> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        return ResponseEntity.ok(warehouseService.updateWarehouse(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate warehouse")
    public ResponseEntity<WarehouseResponse> deactivate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(warehouseService.deactivateWarehouse(id));
    }
}

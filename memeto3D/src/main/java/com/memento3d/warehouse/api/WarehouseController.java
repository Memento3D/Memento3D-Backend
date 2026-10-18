package com.memento3d.warehouse.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.warehouse.api.request.AssignMemberRequest;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.request.UpdateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseMembershipResponse;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.application.service.WarehouseSecurityService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouse Management", description = "Warehouse facility management and access control (F01)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseSecurityService warehouseSecurityService;

    @PostMapping
    @Operation(summary = "Register a new warehouse facility")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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

    @GetMapping("/my-warehouses")
    @Operation(summary = "List warehouses accessible to the current authenticated user (F01)")
    public ResponseEntity<List<WarehouseResponse>> getMyWarehouses(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID userId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        return ResponseEntity.ok(warehouseService.getMyWarehouses(userId, role));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get warehouse details by ID")
    public ResponseEntity<WarehouseResponse> getById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseAccess(id, actorId, role);
        return ResponseEntity.ok(warehouseService.getWarehouse(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update warehouse details")
    public ResponseEntity<WarehouseResponse> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseSecurityService.validateWarehouseManager(id, actorId, role);
        return ResponseEntity.ok(warehouseService.updateWarehouse(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate warehouse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WarehouseResponse> deactivate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(warehouseService.deactivateWarehouse(id));
    }

    // --- Membership & Role Scoping Management (F01) ---

    @GetMapping("/{id}/members")
    @Operation(summary = "List members assigned to warehouse")
    public ResponseEntity<List<WarehouseMembershipResponse>> getMembers(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        return ResponseEntity.ok(warehouseService.getWarehouseMembers(id, actorId, role));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Assign a user to warehouse with a scoped role (MANAGER, WAREHOUSE_STAFF, PRINT_OPERATOR)")
    public ResponseEntity<WarehouseMembershipResponse> assignMember(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignMemberRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(warehouseService.assignMember(id, request, actorId, role));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Revoke user membership from warehouse")
    public ResponseEntity<Void> revokeMember(
            @PathVariable("id") UUID id,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String role = currentUser != null ? currentUser.getRole().name() : "";
        warehouseService.revokeMember(id, userId, actorId, role);
        return ResponseEntity.noContent().build();
    }
}

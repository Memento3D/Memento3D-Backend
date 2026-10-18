package com.memento3d.material.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.material.api.request.CreateMaterialRequest;
import com.memento3d.material.api.request.UpdateMaterialRequest;
import com.memento3d.material.api.response.MaterialResponse;
import com.memento3d.material.application.service.MaterialService;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.domain.model.MaterialType;
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
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
@Tag(name = "Material Catalog", description = "Material definition and catalog management (F01)")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("isAuthenticated()")
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping
    @Operation(summary = "Create a new material")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MaterialResponse> create(
            @Valid @RequestBody CreateMaterialRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        MaterialResponse response = materialService.createMaterial(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/materials/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List materials with optional filters and pagination")
    public ResponseEntity<Page<MaterialResponse>> list(
            @RequestParam(name = "status", required = false) MaterialStatus status,
            @RequestParam(name = "type", required = false) MaterialType materialType,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit, Sort.by(Sort.Direction.ASC, "name"));
        return ResponseEntity.ok(materialService.listMaterials(status, materialType, category, search, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get material details by ID")
    public ResponseEntity<MaterialResponse> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(materialService.getMaterial(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update material details (PUT)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MaterialResponse> updatePut(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateMaterialRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(materialService.updateMaterial(id, request, actorId));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update material details (PATCH)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MaterialResponse> updatePatch(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateMaterialRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(materialService.updateMaterial(id, request, actorId));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle or set material status (ACTIVE/INACTIVE)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<MaterialResponse> updateStatus(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> statusBody,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String statusStr = statusBody.get("status");
        MaterialStatus status = MaterialStatus.valueOf(statusStr.toUpperCase());
        return ResponseEntity.ok(materialService.updateMaterialStatus(id, status, actorId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate material")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MaterialResponse> deactivate(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(materialService.deactivateMaterial(id));
    }
}

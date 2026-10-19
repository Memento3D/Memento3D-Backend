package com.memento3d.customization.api;

import com.memento3d.customization.application.service.CustomizationService;
import com.memento3d.customization.domain.model.CustomizationOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customization Options", description = "Product personalization options (materials, base, engraving, packaging)")
public class CustomizationController {

    private final CustomizationService customizationService;

    @GetMapping("/api/v1/customization-options")
    @Operation(summary = "Get active customization options", description = "Filter by productId or categoryId")
    public ResponseEntity<List<CustomizationOption>> getActiveOptions(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID categoryId
    ) {
        return ResponseEntity.ok(customizationService.getActiveOptions(productId, categoryId));
    }

    @GetMapping("/api/v1/admin/customization-options")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List all customization options", description = "Admin/Manager only")
    public ResponseEntity<List<CustomizationOption>> getAllOptions() {
        return ResponseEntity.ok(customizationService.getAllOptions());
    }

    @PostMapping("/api/v1/admin/customization-options")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Create customization option", description = "Admin/Manager only")
    public ResponseEntity<CustomizationOption> createOption(@Valid @RequestBody CustomizationOption option) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customizationService.createOption(option));
    }

    @PatchMapping("/api/v1/admin/customization-options/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Update customization option", description = "Admin/Manager only")
    public ResponseEntity<CustomizationOption> updateOption(
            @PathVariable UUID id,
            @RequestBody CustomizationOption option
    ) {
        return ResponseEntity.ok(customizationService.updateOption(id, option));
    }
}

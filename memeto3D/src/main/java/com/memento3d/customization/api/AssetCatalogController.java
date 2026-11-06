package com.memento3d.customization.api;

import com.memento3d.customization.api.response.*;
import com.memento3d.customization.application.service.AssetCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @SecurityRequirement(name="BearerAuth")
public class AssetCatalogController {
    private final AssetCatalogService service;
    @GetMapping("/api/v1/customization-catalogs/{code}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary="Get a verified preview asset catalog; returned model URLs expire")
    public CustomizationCatalogResponse get(@PathVariable String code) { return service.getCatalog(code); }
    @PostMapping("/api/v1/admin/customization-catalogs/{code}/import")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary="Verify pinned B2 objects and idempotently import a built-in catalog")
    public CatalogImportResponse importCatalog(@PathVariable String code) { return service.importCatalog(code); }
}

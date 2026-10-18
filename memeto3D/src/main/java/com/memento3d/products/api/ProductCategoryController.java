package com.memento3d.products.api;

import com.memento3d.products.api.request.CreateProductCategoryRequest;
import com.memento3d.products.api.request.UpdateProductCategoryRequest;
import com.memento3d.products.api.response.ProductCategoryResponse;
import com.memento3d.products.application.service.ProductCategoryService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/product-categories", "/api/product-categories"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product Categories", description = "Product category catalog management APIs")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @GetMapping
    @Operation(summary = "Get product categories", description = "Public listing of categories. Staff can view all with all=true.")
    public ResponseEntity<List<ProductCategoryResponse>> listCategories(
            @RequestParam(required = false, defaultValue = "false") boolean all
    ) {
        return ResponseEntity.ok(categoryService.getCategories(!all));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product category by ID")
    public ResponseEntity<ProductCategoryResponse> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get product category by slug")
    public ResponseEntity<ProductCategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getCategoryBySlug(slug));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Create product category", description = "Admin or Manager only")
    public ResponseEntity<ProductCategoryResponse> createCategory(@Valid @RequestBody CreateProductCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Update product category", description = "Admin or Manager only")
    public ResponseEntity<ProductCategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductCategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Toggle category active status", description = "Admin or Manager only")
    public ResponseEntity<ProductCategoryResponse> updateCategoryStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean active = body.getOrDefault("active", true);
        return ResponseEntity.ok(categoryService.updateCategoryStatus(id, active));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Delete category", description = "Admin or Manager only. Fails if category has products.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}


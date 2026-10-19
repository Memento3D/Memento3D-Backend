package com.memento3d.admin.controller;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.products.api.request.CreateProductCategoryRequest;
import com.memento3d.products.api.request.CreateProductRequest;
import com.memento3d.products.api.request.UpdateProductCategoryRequest;
import com.memento3d.products.api.request.UpdateProductRequest;
import com.memento3d.products.api.response.ProductCategoryResponse;
import com.memento3d.products.api.response.ProductResponse;
import com.memento3d.products.application.service.ProductCategoryService;
import com.memento3d.products.application.service.ProductService;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Products", description = "Administration of catalog products and categories with audit logging")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    // --- PRODUCTS ---

    @GetMapping("/products")
    @Operation(summary = "Admin product listing with full filters")
    public ResponseEntity<Page<ProductResponse>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(productService.getProducts(
                keyword, categoryId, categorySlug, minPrice, maxPrice, active, featured, sort, page, size
        ));
    }

    @PostMapping("/products")
    @Operation(summary = "Create product in catalog")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        ProductResponse response = productService.createProduct(request);
        auditLogService.log(actorId, "PRODUCT_CREATED", "Product", response.getId().toString(), null, "Created product: " + response.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/products/{id}")
    @Operation(summary = "Update product details")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        ProductResponse response = productService.updateProduct(id, request);
        auditLogService.log(actorId, "PRODUCT_UPDATED", "Product", id.toString(), null, "Updated product: " + response.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "Delete product from catalog")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        productService.deleteProduct(id);
        auditLogService.log(actorId, "PRODUCT_DELETED", "Product", id.toString(), null, null);
        return ResponseEntity.noContent().build();
    }

    // --- CATEGORIES ---

    @GetMapping("/categories")
    @Operation(summary = "Get all categories (including inactive)")
    public ResponseEntity<List<ProductCategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.getCategories(false));
    }

    @PostMapping("/categories")
    @Operation(summary = "Create new product category")
    public ResponseEntity<ProductCategoryResponse> createCategory(
            @Valid @RequestBody CreateProductCategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        ProductCategoryResponse response = categoryService.createCategory(request);
        auditLogService.log(actorId, "CATEGORY_CREATED", "ProductCategory", response.getId().toString(), null, "Created category: " + response.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/categories/{id}")
    @PutMapping("/categories/{id}")
    @Operation(summary = "Update category details")
    public ResponseEntity<ProductCategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductCategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        ProductCategoryResponse response = categoryService.updateCategory(id, request);
        auditLogService.log(actorId, "CATEGORY_UPDATED", "ProductCategory", id.toString(), null, "Updated category: " + response.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Delete category (guarded: cannot delete if products are assigned)")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        long productsUsingCategory = productRepository.countByCategoryId(id);
        if (productsUsingCategory > 0) {
            throw new BadRequestException("Không thể xóa danh mục đang có " + productsUsingCategory + " sản phẩm sử dụng. Vui lòng chuyển hoặc xóa sản phẩm trước.");
        }
        categoryService.deleteCategory(id);
        auditLogService.log(actorId, "CATEGORY_DELETED", "ProductCategory", id.toString(), null, null);
        return ResponseEntity.noContent().build();
    }
}

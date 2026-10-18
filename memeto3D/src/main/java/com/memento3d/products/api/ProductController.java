package com.memento3d.products.api;

import com.memento3d.products.api.request.CreateProductRequest;
import com.memento3d.products.api.request.UpdateProductRequest;
import com.memento3d.products.api.response.ProductResponse;
import com.memento3d.products.application.service.ProductService;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/products", "/api/products"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Products", description = "Product catalog customer browsing and manager catalog administration")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List and search products", description = "Public product catalog with category, price, search and sort filters")
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
            @RequestParam(defaultValue = "12") int size
    ) {
        // By default, public browse shows only active products unless explicitly requested by staff
        Boolean effectiveActive = active != null ? active : true;
        Page<ProductResponse> products = productService.getProducts(
                keyword, categoryId, categorySlug, minPrice, maxPrice, effectiveActive, featured, sort, page, size
        );
        return ResponseEntity.ok(products);
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured products", description = "List featured products for home page showcase")
    public ResponseEntity<List<ProductResponse>> getFeaturedProducts() {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get product by slug")
    public ResponseEntity<ProductResponse> getProductBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Create product", description = "Admin or Manager only")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Update product", description = "Admin or Manager only")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Toggle product active status", description = "Admin or Manager only")
    public ResponseEntity<ProductResponse> updateProductStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean active = body.getOrDefault("active", true);
        return ResponseEntity.ok(productService.updateProductStatus(id, active));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Delete product", description = "Admin or Manager only")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}

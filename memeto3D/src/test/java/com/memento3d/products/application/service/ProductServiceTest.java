package com.memento3d.products.application.service;

import com.memento3d.products.api.request.CreateProductCategoryRequest;
import com.memento3d.products.api.request.CreateProductRequest;
import com.memento3d.products.api.response.ProductCategoryResponse;
import com.memento3d.products.api.response.ProductResponse;
import com.memento3d.products.application.service.impl.ProductCategoryServiceImpl;
import com.memento3d.products.application.service.impl.ProductServiceImpl;
import com.memento3d.products.domain.exception.CategoryHasProductsException;
import com.memento3d.products.domain.exception.CategoryNotFoundException;
import com.memento3d.products.domain.exception.ProductNotFoundException;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductAvailability;
import com.memento3d.products.domain.model.ProductCategory;
import com.memento3d.products.infrastructure.persistence.repository.ProductCategoryRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @InjectMocks
    private ProductCategoryServiceImpl categoryService;

    private ProductCategory sampleCategory;
    private Product sampleProduct;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        productId = UUID.randomUUID();

        sampleCategory = ProductCategory.builder()
                .id(categoryId)
                .name("Keepsakes")
                .slug("keepsakes")
                .description("Keepsake category")
                .active(true)
                .displayOrder(1)
                .build();

        sampleProduct = Product.builder()
                .id(productId)
                .category(sampleCategory)
                .name("Desk Sculpture 15cm")
                .slug("desk-sculpture-15cm")
                .price(new BigDecimal("350000"))
                .active(true)
                .featured(true)
                .availability(ProductAvailability.AVAILABLE)
                .build();
    }

    @Test
    @DisplayName("Should create category successfully")
    void testCreateCategory() {
        CreateProductCategoryRequest request = CreateProductCategoryRequest.builder()
                .name("Mini Keepsake")
                .slug("mini-keepsake")
                .build();

        when(categoryRepository.existsBySlug("mini-keepsake")).thenReturn(false);
        when(categoryRepository.save(any(ProductCategory.class))).thenReturn(sampleCategory);

        ProductCategoryResponse response = categoryService.createCategory(request);
        assertNotNull(response);
        assertEquals("Keepsakes", response.getName());
    }

    @Test
    @DisplayName("Should prevent category deletion when active products exist")
    void testDeleteCategoryWithProducts() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(productRepository.countByCategoryId(categoryId)).thenReturn(5L);

        assertThrows(CategoryHasProductsException.class, () -> categoryService.deleteCategory(categoryId));
    }

    @Test
    @DisplayName("Should create product successfully with valid category")
    void testCreateProduct() {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(categoryId)
                .name("Desk Sculpture 15cm")
                .price(new BigDecimal("350000"))
                .build();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(productRepository.existsBySlug(anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse response = productService.createProduct(request);
        assertNotNull(response);
        assertEquals("Desk Sculpture 15cm", response.getName());
        assertEquals(new BigDecimal("350000"), response.getPrice());
    }

    @Test
    @DisplayName("Should throw when category not found during product creation")
    void testCreateProductCategoryNotFound() {
        CreateProductRequest request = CreateProductRequest.builder()
                .categoryId(categoryId)
                .name("Desk Sculpture 15cm")
                .price(new BigDecimal("350000"))
                .build();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> productService.createProduct(request));
    }

    @Test
    @DisplayName("Should filter products dynamically")
    void testSearchProducts() {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct));
        when(productRepository.searchProducts(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.getProducts("Desk", null, null, null, null, true, null, "newest", 1, 10);
        assertEquals(1, result.getTotalElements());
        assertEquals("Desk Sculpture 15cm", result.getContent().get(0).getName());
    }
}

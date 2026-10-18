package com.memento3d.products.application.service;

import com.memento3d.products.api.request.CreateProductRequest;
import com.memento3d.products.api.request.UpdateProductRequest;
import com.memento3d.products.api.response.ProductResponse;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductService {

    Page<ProductResponse> getProducts(
            String keyword,
            UUID categoryId,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean active,
            Boolean featured,
            String sort,
            int page,
            int size
    );

    List<ProductResponse> getFeaturedProducts();

    ProductResponse getProductById(UUID id);

    ProductResponse getProductBySlug(String slug);

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(UUID id, UpdateProductRequest request);

    ProductResponse updateProductStatus(UUID id, boolean active);

    void deleteProduct(UUID id);
}

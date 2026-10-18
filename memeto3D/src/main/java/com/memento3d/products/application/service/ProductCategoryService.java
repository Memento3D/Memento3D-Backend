package com.memento3d.products.application.service;

import com.memento3d.products.api.request.CreateProductCategoryRequest;
import com.memento3d.products.api.request.UpdateProductCategoryRequest;
import com.memento3d.products.api.response.ProductCategoryResponse;

import java.util.List;
import java.util.UUID;

public interface ProductCategoryService {

    List<ProductCategoryResponse> getCategories(boolean publicOnly);

    ProductCategoryResponse getCategoryById(UUID id);

    ProductCategoryResponse getCategoryBySlug(String slug);

    ProductCategoryResponse createCategory(CreateProductCategoryRequest request);

    ProductCategoryResponse updateCategory(UUID id, UpdateProductCategoryRequest request);

    ProductCategoryResponse updateCategoryStatus(UUID id, boolean active);

    void deleteCategory(UUID id);
}

package com.memento3d.products.application.service.impl;

import com.memento3d.products.api.request.CreateProductCategoryRequest;
import com.memento3d.products.api.request.UpdateProductCategoryRequest;
import com.memento3d.products.api.response.ProductCategoryResponse;
import com.memento3d.products.application.service.ProductCategoryService;
import com.memento3d.products.domain.exception.CategoryHasProductsException;
import com.memento3d.products.domain.exception.CategoryNotFoundException;
import com.memento3d.products.domain.model.ProductCategory;
import com.memento3d.products.infrastructure.persistence.repository.ProductCategoryRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "#publicOnly")
    public List<ProductCategoryResponse> getCategories(boolean publicOnly) {
        List<ProductCategory> categories = publicOnly
                ? categoryRepository.findByActiveTrueOrderByDisplayOrderAsc()
                : categoryRepository.findAllByOrderByDisplayOrderAsc();

        List<Object[]> rawCounts = publicOnly
                ? productRepository.countActiveProductsGroupedByCategory()
                : productRepository.countAllProductsGroupedByCategory();

        java.util.Map<UUID, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : rawCounts) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID categoryId && row[1] instanceof Number cnt) {
                countMap.put(categoryId, cnt.longValue());
            }
        }

        return categories.stream().map(c -> {
            long count = countMap.getOrDefault(c.getId(), 0L);
            return ProductCategoryResponse.fromEntity(c, count);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductCategoryResponse getCategoryById(UUID id) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + id));
        long count = productRepository.countByCategoryId(category.getId());
        return ProductCategoryResponse.fromEntity(category, count);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductCategoryResponse getCategoryBySlug(String slug) {
        ProductCategory category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with slug: " + slug));
        long count = productRepository.countByCategoryId(category.getId());
        return ProductCategoryResponse.fromEntity(category, count);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public ProductCategoryResponse createCategory(CreateProductCategoryRequest request) {
        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? toSlug(request.getSlug())
                : toSlug(request.getName());

        if (categoryRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        ProductCategory category = ProductCategory.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        ProductCategory saved = categoryRepository.save(category);
        log.info("Created product category ID={}, name={}, slug={}", saved.getId(), saved.getName(), saved.getSlug());
        return ProductCategoryResponse.fromEntity(saved, 0L);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public ProductCategoryResponse updateCategory(UUID id, UpdateProductCategoryRequest request) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            category.setName(request.getName().trim());
        }
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String newSlug = toSlug(request.getSlug());
            if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(newSlug)) {
                newSlug = newSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
            }
            category.setSlug(newSlug);
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getImageUrl() != null) {
            category.setImageUrl(request.getImageUrl());
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        ProductCategory saved = categoryRepository.save(category);
        long count = productRepository.countByCategoryId(saved.getId());
        log.info("Updated product category ID={}, name={}", saved.getId(), saved.getName());
        return ProductCategoryResponse.fromEntity(saved, count);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public ProductCategoryResponse updateCategoryStatus(UUID id, boolean active) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + id));
        category.setActive(active);
        ProductCategory saved = categoryRepository.save(category);
        long count = productRepository.countByCategoryId(saved.getId());
        return ProductCategoryResponse.fromEntity(saved, count);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(UUID id) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + id));

        long count = productRepository.countByCategoryId(id);
        if (count > 0) {
            throw new CategoryHasProductsException("Cannot delete category with ID: " + id + " because it contains " + count + " active products. Deactivate it instead.");
        }

        categoryRepository.delete(category);
        log.info("Deleted product category ID={}", id);
    }

    private String toSlug(String input) {
        if (input == null) return "";
        String nowhitespace = WHITESPACE.matcher(input.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.replaceAll("-+", "-");
    }
}

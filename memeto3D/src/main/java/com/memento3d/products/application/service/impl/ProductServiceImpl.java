package com.memento3d.products.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.products.api.request.CreateProductRequest;
import com.memento3d.products.api.request.UpdateProductRequest;
import com.memento3d.products.api.response.ProductResponse;
import com.memento3d.products.application.service.ProductService;
import com.memento3d.products.domain.exception.CategoryNotFoundException;
import com.memento3d.products.domain.exception.ProductNotFoundException;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductAvailability;
import com.memento3d.products.domain.model.ProductCategory;
import com.memento3d.products.infrastructure.persistence.repository.ProductCategoryRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(
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
    ) {
        int pageNumber = Math.max(0, page > 0 ? page - 1 : 0);
        int pageSize = Math.max(1, Math.min(size, 100));

        Sort sortObj = resolveSort(sort);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortObj);

        Page<Product> productPage = productRepository.searchProducts(
                keyword != null && !keyword.isBlank() ? keyword.trim() : null,
                categoryId,
                categorySlug != null && !categorySlug.isBlank() ? categorySlug.trim() : null,
                minPrice,
                maxPrice,
                active,
                featured,
                pageable
        );

        return productPage.map(ProductResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getFeaturedProducts() {
        return productRepository.findByFeaturedTrueAndActiveTrue()
                .stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findWithCategoryById(id)
                .or(() -> productRepository.findById(id))
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
        return ProductResponse.fromEntity(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with slug: " + slug));
        return ProductResponse.fromEntity(product);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        ProductCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + request.getCategoryId()));

        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? toSlug(request.getSlug())
                : toSlug(request.getName());

        if (productRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        String imagesJson = null;
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            try {
                imagesJson = objectMapper.writeValueAsString(request.getImages());
            } catch (Exception e) {
                imagesJson = String.join(",", request.getImages());
            }
        }

        Product product = Product.builder()
                .category(category)
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .shortDescription(request.getShortDescription())
                .price(request.getPrice())
                .thumbnailUrl(request.getThumbnailUrl())
                .images(imagesJson)
                .active(request.getActive() != null ? request.getActive() : true)
                .featured(request.getFeatured() != null ? request.getFeatured() : false)
                .availability(request.getAvailability() != null ? request.getAvailability() : ProductAvailability.AVAILABLE)
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product ID={}, name={}, slug={}", saved.getId(), saved.getName(), saved.getSlug());
        return ProductResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));

        if (request.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + request.getCategoryId()));
            product.setCategory(category);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName().trim());
        }

        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String newSlug = toSlug(request.getSlug());
            if (!newSlug.equals(product.getSlug()) && productRepository.existsBySlug(newSlug)) {
                newSlug = newSlug + "-" + UUID.randomUUID().toString().substring(0, 6);
            }
            product.setSlug(newSlug);
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getShortDescription() != null) {
            product.setShortDescription(request.getShortDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getThumbnailUrl() != null) {
            product.setThumbnailUrl(request.getThumbnailUrl());
        }
        if (request.getImages() != null) {
            try {
                product.setImages(objectMapper.writeValueAsString(request.getImages()));
            } catch (Exception e) {
                product.setImages(String.join(",", request.getImages()));
            }
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        if (request.getFeatured() != null) {
            product.setFeatured(request.getFeatured());
        }
        if (request.getAvailability() != null) {
            product.setAvailability(request.getAvailability());
        }

        Product saved = productRepository.save(product);
        log.info("Updated product ID={}, name={}", saved.getId(), saved.getName());
        return ProductResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProductStatus(UUID id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
        product.setActive(active);
        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
        productRepository.delete(product);
        log.info("Deleted product ID={}", id);
    }

    private Sort resolveSort(String sort) {
        if (sort == null) return Sort.by(Sort.Direction.DESC, "createdAt");
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "priceasc", "price_asc", "price,asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "pricedesc", "price_desc", "price,desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "popular" -> Sort.by(Sort.Direction.DESC, "featured").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private String toSlug(String input) {
        if (input == null) return "";
        String nowhitespace = WHITESPACE.matcher(input.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        return slug.replaceAll("-+", "-");
    }
}

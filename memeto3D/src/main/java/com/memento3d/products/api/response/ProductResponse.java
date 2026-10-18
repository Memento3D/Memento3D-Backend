package com.memento3d.products.api.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductAvailability;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String categorySlug;
    private String name;
    private String slug;
    private String description;
    private String shortDescription;
    private BigDecimal price;
    private String thumbnailUrl;
    private List<String> images;
    private boolean active;
    private boolean featured;
    private ProductAvailability availability;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductResponse fromEntity(Product product) {
        List<String> imageList = new ArrayList<>();
        if (product.getImages() != null && !product.getImages().isBlank()) {
            try {
                if (product.getImages().trim().startsWith("[")) {
                    imageList = OBJECT_MAPPER.readValue(product.getImages(), new TypeReference<List<String>>() {});
                } else {
                    imageList = List.of(product.getImages().split(","));
                }
            } catch (Exception ignored) {
                imageList.add(product.getImages());
            }
        }
        if (imageList.isEmpty() && product.getThumbnailUrl() != null) {
            imageList.add(product.getThumbnailUrl());
        }

        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .price(product.getPrice())
                .thumbnailUrl(product.getThumbnailUrl())
                .images(imageList)
                .active(product.isActive())
                .featured(product.isFeatured())
                .availability(product.getAvailability())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}

package com.memento3d.products.api.response;

import com.memento3d.products.domain.model.ProductCategory;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategoryResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private boolean active;
    private Integer displayOrder;
    private Long productCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductCategoryResponse fromEntity(ProductCategory category, Long count) {
        return ProductCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .active(category.isActive())
                .displayOrder(category.getDisplayOrder())
                .productCount(count != null ? count : 0L)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}

package com.memento3d.products.api.request;

import com.memento3d.products.domain.model.ProductAvailability;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProductRequest {

    private UUID categoryId;
    private String name;
    private String slug;
    private String description;
    private String shortDescription;

    @PositiveOrZero(message = "Price must be non-negative")
    private BigDecimal price;

    private String thumbnailUrl;
    private List<String> images;
    private Boolean active;
    private Boolean featured;
    private ProductAvailability availability;
}

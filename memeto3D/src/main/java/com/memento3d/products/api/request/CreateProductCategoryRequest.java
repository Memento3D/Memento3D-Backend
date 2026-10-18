package com.memento3d.products.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductCategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String slug;
    private String description;
    private String imageUrl;
    private Boolean active;
    private Integer displayOrder;
}

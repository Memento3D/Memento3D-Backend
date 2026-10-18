package com.memento3d.products.api.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProductCategoryRequest {

    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private Boolean active;
    private Integer displayOrder;
}

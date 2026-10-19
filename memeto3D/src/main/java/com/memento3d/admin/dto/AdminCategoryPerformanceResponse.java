package com.memento3d.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Category order and revenue performance")
public class AdminCategoryPerformanceResponse {

    private List<CategoryMetric> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryMetric {
        private String categoryName;
        private String categorySlug;
        private long orderCount;
        private BigDecimal totalRevenue;
    }
}

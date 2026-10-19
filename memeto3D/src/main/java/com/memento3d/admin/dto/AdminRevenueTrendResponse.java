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
@Schema(description = "Revenue trend chart data response")
public class AdminRevenueTrendResponse {

    @Schema(description = "Requested period (today, 7d, 30d, 3m, 6m, 1y)", example = "30d")
    private String period;

    @Schema(description = "Total revenue over the period")
    private BigDecimal totalRevenue;

    @Schema(description = "Total order count over the period")
    private long totalOrders;

    @Schema(description = "Timeline data points")
    private List<RevenueDataPoint> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueDataPoint {
        private String date;
        private BigDecimal revenue;
        private long orders;
    }
}

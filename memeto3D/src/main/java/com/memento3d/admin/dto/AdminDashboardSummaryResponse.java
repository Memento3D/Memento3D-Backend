package com.memento3d.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Global executive dashboard statistics summary")
public class AdminDashboardSummaryResponse {

    @Schema(description = "User accounts statistics")
    private UserStats users;

    @Schema(description = "Platform orders statistics")
    private OrderStats orders;

    @Schema(description = "Financial revenue metrics")
    private RevenueStats revenue;

    @Schema(description = "Catalog metrics")
    private CatalogStats catalog;

    @Schema(description = "3D AI Reconstruction metrics")
    private ReconstructionStats reconstruction;

    @Schema(description = "Hardware printer fleet metrics")
    private PrinterStats printers;

    @Schema(description = "Real-time system health summary")
    private Map<String, String> systemHealth;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStats {
        private long total;
        private long active;
        private long inactive;
        private long locked;
        private long newToday;
        private long newThisMonth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStats {
        private long total;
        private long ordersToday;
        private long pending;
        private long processing;
        private long printing;
        private long completed;
        private long cancelled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueStats {
        private BigDecimal today;
        private BigDecimal thisMonth;
        private BigDecimal total;
        private BigDecimal averageOrderValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogStats {
        private long totalProducts;
        private long totalCategories;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconstructionStats {
        private long total;
        private long queued;
        private long running;
        private long completed;
        private long failed;
        private long previewJobs;
        private long premiumJobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrinterStats {
        private long total;
        private long printersOnline;
        private long printersOffline;
    }
}

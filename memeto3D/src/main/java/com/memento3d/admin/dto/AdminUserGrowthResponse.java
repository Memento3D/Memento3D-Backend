package com.memento3d.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User growth chart data response")
public class AdminUserGrowthResponse {

    @Schema(description = "Requested period (7d, 30d, 90d, 1y)", example = "30d")
    private String period;

    @Schema(description = "User growth data points")
    private List<UserGrowthPoint> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserGrowthPoint {
        private String date;
        private long registrations;
        private long cumulativeTotal;
    }
}

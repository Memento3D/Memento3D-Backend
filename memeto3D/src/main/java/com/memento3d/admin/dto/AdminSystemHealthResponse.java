package com.memento3d.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Real-time infrastructure system health diagnostic")
public class AdminSystemHealthResponse {

    @Schema(description = "Overall health status (HEALTHY, DEGRADED, DOWN)", example = "HEALTHY")
    private String status;

    private Instant timestamp;

    @Schema(description = "Service components and their verified status")
    private Map<String, ServiceStatus> services;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStatus {
        private String status; // HEALTHY, DEGRADED, DOWN
        private String message;
        private Long latencyMs;
        private Map<String, Object> details;
    }
}

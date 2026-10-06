package com.memeto.team.memeto3d.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Redis health and monitoring status")
public class RedisHealthResponse {

    @Schema(description = "Overall status (UP/DOWN)", example = "UP")
    private String status;

    @Schema(description = "Redis latency / ping response time in milliseconds", example = "2.4")
    private double responseTimeMs;

    @Schema(description = "Redis ping result", example = "PONG")
    private String ping;

    @Schema(description = "Detailed Redis metrics and key counts")
    private Map<String, Object> details;
}

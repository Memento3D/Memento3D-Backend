package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.RedisHealthResponse;
import com.memeto.team.memeto3d.service.RedisMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/redis")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Redis Monitoring", description = "Redis health check and monitoring endpoints")
public class RedisMonitoringController {

    private final RedisMonitoringService redisMonitoringService;

    @GetMapping("/health")
    @Operation(summary = "Get Redis Health & Metrics", description = "Performs Redis PING latency test and returns memory & key statistics")
    public ResponseEntity<RedisHealthResponse> getHealth() {
        RedisHealthResponse response = redisMonitoringService.checkHealth();
        return ResponseEntity.ok(response);
    }
}

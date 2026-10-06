package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.RedisHealthResponse;
import com.memeto.team.memeto3d.service.RedisMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisMonitoringServiceImpl implements RedisMonitoringService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public RedisHealthResponse checkHealth() {
        long startTime = System.nanoTime();
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            String pingResult = connection.ping();
            long endTime = System.nanoTime();
            double responseTimeMs = (endTime - startTime) / 1_000_000.0;

            Map<String, Object> details = new HashMap<>();
            details.put("databaseSize", connection.dbSize());

            try {
                Properties info = connection.info("memory");
                if (info != null) {
                    details.put("usedMemoryHuman", info.getProperty("used_memory_human", "N/A"));
                }
            } catch (Exception ignored) {}

            try {
                Set<String> userKeys = redisTemplate.keys("user:*");
                Set<String> scanKeys = redisTemplate.keys("scan:*");
                Set<String> authKeys = redisTemplate.keys("auth:*");
                details.put("cachedUserCount", userKeys != null ? userKeys.size() : 0);
                details.put("cachedScanCount", scanKeys != null ? scanKeys.size() : 0);
                details.put("cachedAuthCount", authKeys != null ? authKeys.size() : 0);
            } catch (Exception ignored) {}

            connection.close();

            log.info("Redis health check: UP (latency: {} ms, ping: {})", responseTimeMs, pingResult);

            return RedisHealthResponse.builder()
                    .status("UP")
                    .responseTimeMs(Math.round(responseTimeMs * 100.0) / 100.0)
                    .ping(pingResult)
                    .details(details)
                    .build();
        } catch (Exception e) {
            long endTime = System.nanoTime();
            double responseTimeMs = (endTime - startTime) / 1_000_000.0;

            log.warn("Redis health check: DOWN (latency: {} ms, error: {})", responseTimeMs, e.getMessage());

            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());

            return RedisHealthResponse.builder()
                    .status("DOWN")
                    .responseTimeMs(Math.round(responseTimeMs * 100.0) / 100.0)
                    .ping("FAILED")
                    .details(details)
                    .build();
        }
    }
}

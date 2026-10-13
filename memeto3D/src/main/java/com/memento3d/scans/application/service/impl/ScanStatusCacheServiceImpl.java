package com.memento3d.scans.application.service.impl;

import com.memento3d.scans.domain.model.Scan;

import com.memento3d.scans.api.response.ScanStatusResponse;
import com.memento3d.scans.application.service.ScanStatusCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanStatusCacheServiceImpl implements ScanStatusCacheService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.cache.scan-status-ttl-hours:6}")
    private long statusTtlHours;

    private String buildStatusKey(UUID scanId) {
        return "scan:" + scanId + ":status";
    }

    private String buildProgressKey(UUID scanId) {
        return "scan:" + scanId + ":progress";
    }

    private String buildMessageKey(UUID scanId) {
        return "scan:" + scanId + ":message";
    }

    @Override
    public Optional<String> getScanStatus(UUID scanId) {
        if (scanId == null) {
            return Optional.empty();
        }
        String key = buildStatusKey(scanId);
        try {
            String status = redisTemplate.opsForValue().get(key);
            if (status != null && !status.isBlank()) {
                log.debug("Cache HIT for scan status key {}: {}", key, status);
                return Optional.of(status);
            }
            log.debug("Cache MISS for scan status key: {}", key);
        } catch (Exception e) {
            log.warn("Redis error fetching scan status for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getScanProgress(UUID scanId) {
        if (scanId == null) {
            return Optional.empty();
        }
        String key = buildProgressKey(scanId);
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null && !val.isBlank()) {
                return Optional.of(Integer.parseInt(val.trim()));
            }
        } catch (Exception e) {
            log.warn("Redis error fetching scan progress for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getScanMessage(UUID scanId) {
        if (scanId == null) {
            return Optional.empty();
        }
        String key = buildMessageKey(scanId);
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null && !val.isBlank()) {
                return Optional.of(val);
            }
        } catch (Exception e) {
            log.warn("Redis error fetching scan message for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<ScanStatusResponse> getScanStatusDetails(UUID scanId) {
        if (scanId == null) {
            return Optional.empty();
        }
        Optional<String> statusOpt = getScanStatus(scanId);
        if (statusOpt.isEmpty()) {
            return Optional.empty();
        }

        String status = statusOpt.get();
        Integer progress = getScanProgress(scanId).orElseGet(() -> getDefaultProgressForStatus(status));
        String message = getScanMessage(scanId).orElseGet(() -> getDefaultMessageForStatus(status));

        ScanStatusResponse response = ScanStatusResponse.builder()
                .scanId(scanId.toString())
                .status(status)
                .progress(progress)
                .message(message)
                .build();

        return Optional.of(response);
    }

    @Override
    public void cacheScanStatus(UUID scanId, String status) {
        if (scanId == null || status == null || status.isBlank()) {
            return;
        }
        String key = buildStatusKey(scanId);
        try {
            redisTemplate.opsForValue().set(key, status, Duration.ofHours(statusTtlHours));
            log.debug("Cached scan status {} -> {} in Redis with TTL {} hour(s)", key, status, statusTtlHours);
        } catch (Exception e) {
            log.warn("Redis error setting scan status cache for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void cacheScanProgress(UUID scanId, int progress) {
        if (scanId == null) {
            return;
        }
        String key = buildProgressKey(scanId);
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(progress), Duration.ofHours(statusTtlHours));
            log.debug("Cached scan progress {} -> {} in Redis with TTL {} hour(s)", key, progress, statusTtlHours);
        } catch (Exception e) {
            log.warn("Redis error setting scan progress cache for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void cacheScanMessage(UUID scanId, String message) {
        if (scanId == null || message == null || message.isBlank()) {
            return;
        }
        String key = buildMessageKey(scanId);
        try {
            redisTemplate.opsForValue().set(key, message, Duration.ofHours(statusTtlHours));
            log.debug("Cached scan message {} -> {} in Redis with TTL {} hour(s)", key, message, statusTtlHours);
        } catch (Exception e) {
            log.warn("Redis error setting scan message cache for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void cacheScanStatusDetails(UUID scanId, String status, Integer progress, String message) {
        if (scanId == null) {
            return;
        }
        if (status != null && !status.isBlank()) {
            cacheScanStatus(scanId, status);
        }
        if (progress != null) {
            cacheScanProgress(scanId, progress);
        }
        if (message != null && !message.isBlank()) {
            cacheScanMessage(scanId, message);
        }
    }

    @Override
    public void evictScanStatus(UUID scanId) {
        if (scanId == null) {
            return;
        }
        try {
            redisTemplate.delete(buildStatusKey(scanId));
            redisTemplate.delete(buildProgressKey(scanId));
            redisTemplate.delete(buildMessageKey(scanId));
            log.debug("Evicted scan status, progress, and message cache for scanId: {}", scanId);
        } catch (Exception e) {
            log.warn("Redis error evicting scan status cache for scanId {}: {}", scanId, e.getMessage());
        }
    }

    public static int getDefaultProgressForStatus(String status) {
        if (status == null) return 0;
        switch (status.toUpperCase()) {
            case "COMPLETED":
            case "DONE":
                return 100;
            case "PROCESSING":
                return 0;
            default:
                return 0;
        }
    }

    public static String getDefaultMessageForStatus(String status) {
        if (status == null) return "";
        switch (status.toUpperCase()) {
            case "PROCESSING":
                return "Generating 3D model";
            case "COMPLETED":
            case "DONE":
                return "Reconstruction completed successfully";
            case "FAILED":
                return "Reconstruction failed";
            case "UPLOADING":
                return "Uploading scan files";
            case "READY":
                return "Ready for reconstruction";
            case "DRAFT":
            case "CREATED":
                return "Draft scan session created";
            default:
                return status;
        }
    }
}

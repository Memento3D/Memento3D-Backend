package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.service.CacheInvalidationService;
import com.memeto.team.memeto3d.service.ScanStatusCacheService;
import com.memeto.team.memeto3d.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationServiceImpl implements CacheInvalidationService {

    private final UserCacheService userCacheService;
    private final ScanStatusCacheService scanStatusCacheService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void invalidateUserCache(UUID userId) {
        if (userId == null) return;
        userCacheService.evictUser(userId);
        log.info("Invalidated user cache for userId: {}", userId);
    }

    @Override
    public void invalidateScanCache(UUID scanId) {
        if (scanId == null) return;
        scanStatusCacheService.evictScanStatus(scanId);
        log.info("Invalidated scan cache for scanId: {}", scanId);
    }

    @Override
    public void invalidateAllUserCaches() {
        try {
            Set<String> keys = redisTemplate.keys("user:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated all user cache keys: count={}", keys.size());
            }
        } catch (Exception e) {
            log.warn("Error invalidating all user caches: {}", e.getMessage());
        }
    }

    @Override
    public void invalidateAllScanCaches() {
        try {
            Set<String> keys = redisTemplate.keys("scan:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated all scan cache keys: count={}", keys.size());
            }
        } catch (Exception e) {
            log.warn("Error invalidating all scan caches: {}", e.getMessage());
        }
    }
}

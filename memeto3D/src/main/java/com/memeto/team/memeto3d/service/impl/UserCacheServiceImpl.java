package com.memeto.team.memeto3d.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memeto.team.memeto3d.dto.UserResponse;
import com.memeto.team.memeto3d.service.UserCacheService;
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
public class UserCacheServiceImpl implements UserCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.user-ttl-hours:1}")
    private long userTtlHours;

    private String buildKey(UUID userId) {
        return "user:" + userId;
    }

    @Override
    public Optional<UserResponse> getUserFromCache(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String key = buildKey(userId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                UserResponse user = objectMapper.readValue(json, UserResponse.class);
                log.debug("Cache HIT for user key: {}", key);
                return Optional.of(user);
            }
            log.debug("Cache MISS for user key: {}", key);
        } catch (Exception e) {
            log.warn("Redis error fetching user cache for key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void cacheUser(UUID userId, UserResponse userResponse) {
        if (userId == null || userResponse == null) {
            return;
        }
        String key = buildKey(userId);
        try {
            String json = objectMapper.writeValueAsString(userResponse);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(userTtlHours));
            log.debug("Cached user {} in Redis with TTL {} hour(s)", key, userTtlHours);
        } catch (Exception e) {
            log.warn("Redis error setting user cache for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void evictUser(UUID userId) {
        if (userId == null) {
            return;
        }
        String key = buildKey(userId);
        try {
            redisTemplate.delete(key);
            log.debug("Evicted user cache key: {}", key);
        } catch (Exception e) {
            log.warn("Redis error evicting user cache for key {}: {}", key, e.getMessage());
        }
    }
}

package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.domain.model.RefreshToken;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.application.service.RefreshTokenService;
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
public class RedisRefreshTokenServiceImpl implements RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.auth.refresh-token-ttl-days:7}")
    private long refreshTokenTtlDays;

    private String buildTokenKey(String refreshToken) {
        return "auth:refresh_token:" + refreshToken;
    }

    private String buildUserKey(UUID userId) {
        return "auth:user_refresh:" + userId;
    }

    @Override
    public String createRefreshToken(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null when generating refresh token");
        }

        revokeUserRefreshTokens(userId);

        String refreshToken = UUID.randomUUID().toString();
        String tokenKey = buildTokenKey(refreshToken);
        String userKey = buildUserKey(userId);
        Duration ttl = Duration.ofDays(refreshTokenTtlDays);

        try {
            redisTemplate.opsForValue().set(tokenKey, userId.toString(), ttl);
            redisTemplate.opsForValue().set(userKey, refreshToken, ttl);
            log.info("Generated refresh token for user {} stored in Redis with TTL {} days", userId, refreshTokenTtlDays);
        } catch (Exception e) {
            log.warn("Redis error creating refresh token for user {}: {}", userId, e.getMessage());
        }

        return refreshToken;
    }

    @Override
    public String createRefreshToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null when generating refresh token");
        }
        return createRefreshToken(user.getId());
    }

    @Override
    public Optional<UUID> validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }

        String tokenKey = buildTokenKey(refreshToken);
        try {
            String userIdStr = redisTemplate.opsForValue().get(tokenKey);
            if (userIdStr != null && !userIdStr.isBlank()) {
                log.debug("Valid refresh token found in Redis for user {}", userIdStr);
                return Optional.of(UUID.fromString(userIdStr));
            }
        } catch (Exception e) {
            log.warn("Redis error validating refresh token {}: {}", refreshToken, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public Optional<RefreshToken> findByToken(String refreshToken) {
        return Optional.empty();
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenKey = buildTokenKey(refreshToken);
        try {
            String userIdStr = redisTemplate.opsForValue().get(tokenKey);
            if (userIdStr != null && !userIdStr.isBlank()) {
                UUID userId = UUID.fromString(userIdStr);
                redisTemplate.delete(buildUserKey(userId));
            }
            redisTemplate.delete(tokenKey);
            log.info("Revoked refresh token {}", refreshToken);
        } catch (Exception e) {
            log.warn("Redis error revoking refresh token {}: {}", refreshToken, e.getMessage());
        }
    }

    @Override
    public void revokeUserRefreshTokens(UUID userId) {
        if (userId == null) {
            return;
        }

        String userKey = buildUserKey(userId);
        try {
            String existingToken = redisTemplate.opsForValue().get(userKey);
            if (existingToken != null && !existingToken.isBlank()) {
                redisTemplate.delete(buildTokenKey(existingToken));
            }
            redisTemplate.delete(userKey);
            log.info("Revoked all refresh tokens for user {}", userId);
        } catch (Exception e) {
            log.warn("Redis error revoking refresh tokens for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void deleteExpiredTokens() {
        // Redis automatically manages expiration via TTL
    }
}

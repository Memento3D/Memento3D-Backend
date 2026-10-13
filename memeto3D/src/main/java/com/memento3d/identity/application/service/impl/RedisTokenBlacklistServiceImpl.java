package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.domain.model.BlacklistedToken;
import com.memento3d.identity.infrastructure.persistence.repository.BlacklistedTokenRepository;
import com.memento3d.identity.application.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class RedisTokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    private String buildKey(String token) {
        return "auth:blacklist:" + token;
    }

    @Override
    public void blacklistToken(String token, Instant expiryDate) {
        if (token == null || token.isBlank()) {
            return;
        }

        String key = buildKey(token);
        Instant now = Instant.now();
        Instant expiry = expiryDate != null ? expiryDate : now.plusSeconds(3600);
        long ttlSeconds = Duration.between(now, expiry).getSeconds();

        if (ttlSeconds <= 0) {
            ttlSeconds = 3600;
        }

        // 1. Save in Redis
        try {
            redisTemplate.opsForValue().set(key, "true", Duration.ofSeconds(ttlSeconds));
            log.info("Token blacklisted in Redis key '{}' with TTL {} seconds", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Redis error blacklisting token: {}", e.getMessage());
        }

        // 2. Save in PostgreSQL fallback
        try {
            if (!blacklistedTokenRepository.existsByToken(token)) {
                BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                        .token(token)
                        .expiryDate(expiry)
                        .build();
                blacklistedTokenRepository.save(blacklistedToken);
            }
        } catch (Exception e) {
            log.warn("DB error persisting blacklisted token: {}", e.getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String key = buildKey(token);
        // 1. Fast Redis check
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                log.debug("Redis HIT for token blacklist key: {}", key);
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis error checking token blacklist for key {}: {}. Falling back to DB.", key, e.getMessage());
        }

        // 2. Fallback DB check
        try {
            boolean inDb = blacklistedTokenRepository.existsByToken(token);
            if (inDb) {
                try {
                    redisTemplate.opsForValue().set(key, "true", Duration.ofHours(1));
                } catch (Exception ignored) {}
            }
            return inDb;
        } catch (Exception e) {
            log.error("DB error checking token blacklist: {}", e.getMessage());
            return false;
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredTokens() {
        log.info("Purging expired blacklisted tokens from DB...");
        try {
            blacklistedTokenRepository.deleteExpiredTokens(Instant.now());
        } catch (Exception e) {
            log.warn("Error purging expired blacklisted tokens: {}", e.getMessage());
        }
    }
}

package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed implementation of {@link TokenBlacklistService}.
 * <p>
 * Blacklisted tokens are stored as Redis keys with a TTL equal to their
 * remaining validity period. Once the TTL expires, Redis automatically
 * removes the key — no scheduled purge job required.
 * <p>
 * Marked as {@code @Primary} so Spring injects this instead of the
 * database-backed {@link DatabaseTokenBlacklistService}.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void blacklistToken(String token, Instant expiryDate) {
        if (token == null || token.isBlank()) {
            return;
        }

        String key = BLACKLIST_PREFIX + token;
        Instant expiry = expiryDate != null ? expiryDate : Instant.now().plusSeconds(3600);
        Duration ttl = Duration.between(Instant.now(), expiry);

        if (ttl.isNegative() || ttl.isZero()) {
            log.debug("Token already expired, skipping blacklist");
            return;
        }

        redisTemplate.opsForValue().set(key, "blacklisted", ttl);
        log.info("Token blacklisted in Redis with TTL of {} seconds", ttl.getSeconds());
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}

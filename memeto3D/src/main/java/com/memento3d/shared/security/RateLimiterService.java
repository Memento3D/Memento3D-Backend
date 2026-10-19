package com.memento3d.shared.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Fixed-window rate limiter backed by Redis ({@code INCR} + {@code EXPIRE}).
 * Fails open when Redis is unreachable so an infrastructure hiccup cannot lock users out;
 * endpoints that depend on Redis for correctness (reset tokens) fail on their own.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    /**
     * @return {@code true} when the call is within the limit, {@code false} when it must be rejected.
     */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L || count > maxAttempts) {
                // Heal keys that lost their TTL (e.g. crash between INCR and EXPIRE).
                Long ttl = redisTemplate.getExpire(key);
                if (count == 1L || ttl == null || ttl < 0) {
                    redisTemplate.expire(key, window);
                }
            }
            return count <= maxAttempts;
        } catch (Exception e) {
            log.warn("Rate limiter unavailable for key prefix '{}': {}", prefix(key), e.getMessage());
            return true;
        }
    }

    /** Clears a counter (e.g. after a successful operation). */
    public void reset(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Could not reset rate limiter key: {}", e.getMessage());
        }
    }

    private static String prefix(String key) {
        int idx = key.lastIndexOf(':');
        return idx > 0 ? key.substring(0, idx) : key;
    }
}

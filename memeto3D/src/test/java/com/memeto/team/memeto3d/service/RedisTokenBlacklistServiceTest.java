package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.repository.BlacklistedTokenRepository;
import com.memeto.team.memeto3d.service.impl.RedisTokenBlacklistServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @InjectMocks
    private RedisTokenBlacklistServiceImpl blacklistService;

    @Test
    void blacklistToken_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "jwt.token.here";
        Instant expiry = Instant.now().plusSeconds(3600);

        blacklistService.blacklistToken(token, expiry);

        verify(valueOperations, times(1)).set(eq("auth:blacklist:" + token), eq("true"), any(Duration.class));
        verify(blacklistedTokenRepository, times(1)).save(any());
    }

    @Test
    void isBlacklisted_RedisHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "jwt.token.here";
        when(valueOperations.get("auth:blacklist:" + token)).thenReturn("true");

        boolean result = blacklistService.isBlacklisted(token);

        assertTrue(result);
        verify(blacklistedTokenRepository, never()).existsByToken(any());
    }

    @Test
    void isBlacklisted_RedisMissDbHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "jwt.token.here";
        when(valueOperations.get("auth:blacklist:" + token)).thenReturn(null);
        when(blacklistedTokenRepository.existsByToken(token)).thenReturn(true);

        boolean result = blacklistService.isBlacklisted(token);

        assertTrue(result);
        verify(blacklistedTokenRepository, times(1)).existsByToken(token);
    }
}

package com.memento3d.identity.application.service.impl;

import com.memento3d.identity.application.service.RefreshTokenService;

import com.memento3d.identity.application.service.impl.RedisRefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisRefreshTokenServiceImpl refreshTokenService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenTtlDays", 7L);
    }

    @Test
    void createRefreshToken_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String token = refreshTokenService.createRefreshToken(userId);

        assertNotNull(token);
        verify(valueOperations, times(1)).set(eq("auth:refresh_token:" + token), eq(userId.toString()), eq(Duration.ofDays(7)));
        verify(valueOperations, times(1)).set(eq("auth:user_refresh:" + userId), eq(token), eq(Duration.ofDays(7)));
    }

    @Test
    void validateRefreshToken_Hit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "sample-refresh-token";
        when(valueOperations.get("auth:refresh_token:" + token)).thenReturn(userId.toString());

        Optional<UUID> result = refreshTokenService.validateRefreshToken(token);

        assertTrue(result.isPresent());
        assertEquals(userId, result.get());
    }

    @Test
    void validateRefreshToken_Miss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "invalid-token";
        when(valueOperations.get("auth:refresh_token:" + token)).thenReturn(null);

        Optional<UUID> result = refreshTokenService.validateRefreshToken(token);

        assertFalse(result.isPresent());
    }

    @Test
    void revokeRefreshToken_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "sample-refresh-token";
        when(valueOperations.get("auth:refresh_token:" + token)).thenReturn(userId.toString());

        refreshTokenService.revokeRefreshToken(token);

        verify(redisTemplate, times(1)).delete("auth:user_refresh:" + userId);
        verify(redisTemplate, times(1)).delete("auth:refresh_token:" + token);
    }
}

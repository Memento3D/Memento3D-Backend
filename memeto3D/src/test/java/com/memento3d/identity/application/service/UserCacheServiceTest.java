package com.memento3d.identity.application.service;

import com.memento3d.identity.domain.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.application.service.impl.UserCacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserCacheServiceImpl userCacheService;

    private UUID userId;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userResponse = UserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        ReflectionTestUtils.setField(userCacheService, "userTtlHours", 1L);
    }

    @Test
    void getUserFromCache_Hit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String json = objectMapper.writeValueAsString(userResponse);
        when(valueOperations.get("user:" + userId)).thenReturn(json);

        Optional<UserResponse> result = userCacheService.getUserFromCache(userId);

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
        assertEquals("test@example.com", result.get().getEmail());
    }

    @Test
    void getUserFromCache_Miss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:" + userId)).thenReturn(null);

        Optional<UserResponse> result = userCacheService.getUserFromCache(userId);

        assertFalse(result.isPresent());
    }

    @Test
    void getUserFromCache_RedisException_HandledGracefully() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:" + userId)).thenThrow(new RuntimeException("Redis connection error"));

        Optional<UserResponse> result = userCacheService.getUserFromCache(userId);

        assertFalse(result.isPresent());
    }

    @Test
    void cacheUser_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        userCacheService.cacheUser(userId, userResponse);

        verify(valueOperations, times(1)).set(eq("user:" + userId), anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    void evictUser_Success() {
        userCacheService.evictUser(userId);

        verify(redisTemplate, times(1)).delete("user:" + userId);
    }
}

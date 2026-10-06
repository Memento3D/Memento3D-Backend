package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.ScanStatusResponse;
import com.memeto.team.memeto3d.service.impl.ScanStatusCacheServiceImpl;
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
class ScanStatusCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ScanStatusCacheServiceImpl scanStatusCacheService;

    private UUID scanId;

    @BeforeEach
    void setUp() {
        scanId = UUID.randomUUID();
        ReflectionTestUtils.setField(scanStatusCacheService, "statusTtlHours", 6L);
    }

    @Test
    void getScanStatus_Hit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("scan:" + scanId + ":status")).thenReturn("PROCESSING");

        Optional<String> result = scanStatusCacheService.getScanStatus(scanId);

        assertTrue(result.isPresent());
        assertEquals("PROCESSING", result.get());
    }

    @Test
    void getScanStatus_Miss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("scan:" + scanId + ":status")).thenReturn(null);

        Optional<String> result = scanStatusCacheService.getScanStatus(scanId);

        assertFalse(result.isPresent());
    }

    @Test
    void getScanStatus_RedisException_HandledGracefully() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("scan:" + scanId + ":status")).thenThrow(new RuntimeException("Redis connection error"));

        Optional<String> result = scanStatusCacheService.getScanStatus(scanId);

        assertFalse(result.isPresent());
    }

    @Test
    void getScanStatusDetails_HitWithAllKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("scan:" + scanId + ":status")).thenReturn("PROCESSING");
        when(valueOperations.get("scan:" + scanId + ":progress")).thenReturn("65");
        when(valueOperations.get("scan:" + scanId + ":message")).thenReturn("Generating 3D model");

        Optional<ScanStatusResponse> result = scanStatusCacheService.getScanStatusDetails(scanId);

        assertTrue(result.isPresent());
        ScanStatusResponse resp = result.get();
        assertEquals(scanId.toString(), resp.getScanId());
        assertEquals("PROCESSING", resp.getStatus());
        assertEquals(65, resp.getProgress());
        assertEquals("Generating 3D model", resp.getMessage());
    }

    @Test
    void getScanStatusDetails_HitWithDefaults() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("scan:" + scanId + ":status")).thenReturn("PROCESSING");
        when(valueOperations.get("scan:" + scanId + ":progress")).thenReturn(null);
        when(valueOperations.get("scan:" + scanId + ":message")).thenReturn(null);

        Optional<ScanStatusResponse> result = scanStatusCacheService.getScanStatusDetails(scanId);

        assertTrue(result.isPresent());
        ScanStatusResponse resp = result.get();
        assertEquals(scanId.toString(), resp.getScanId());
        assertEquals("PROCESSING", resp.getStatus());
        assertEquals(0, resp.getProgress());
        assertEquals("Generating 3D model", resp.getMessage());
    }

    @Test
    void getScanStatusDetails_Miss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("scan:" + scanId + ":status")).thenReturn(null);

        Optional<ScanStatusResponse> result = scanStatusCacheService.getScanStatusDetails(scanId);

        assertFalse(result.isPresent());
    }

    @Test
    void cacheScanStatus_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        scanStatusCacheService.cacheScanStatus(scanId, "UPLOADING");

        verify(valueOperations, times(1)).set(eq("scan:" + scanId + ":status"), eq("UPLOADING"), eq(Duration.ofHours(6)));
    }

    @Test
    void cacheScanProgress_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        scanStatusCacheService.cacheScanProgress(scanId, 65);

        verify(valueOperations, times(1)).set(eq("scan:" + scanId + ":progress"), eq("65"), eq(Duration.ofHours(6)));
    }

    @Test
    void cacheScanStatusDetails_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        scanStatusCacheService.cacheScanStatusDetails(scanId, "PROCESSING", 65, "Generating 3D model");

        verify(valueOperations, times(1)).set(eq("scan:" + scanId + ":status"), eq("PROCESSING"), eq(Duration.ofHours(6)));
        verify(valueOperations, times(1)).set(eq("scan:" + scanId + ":progress"), eq("65"), eq(Duration.ofHours(6)));
        verify(valueOperations, times(1)).set(eq("scan:" + scanId + ":message"), eq("Generating 3D model"), eq(Duration.ofHours(6)));
    }

    @Test
    void evictScanStatus_Success() {
        scanStatusCacheService.evictScanStatus(scanId);

        verify(redisTemplate, times(1)).delete("scan:" + scanId + ":status");
        verify(redisTemplate, times(1)).delete("scan:" + scanId + ":progress");
        verify(redisTemplate, times(1)).delete("scan:" + scanId + ":message");
    }
}

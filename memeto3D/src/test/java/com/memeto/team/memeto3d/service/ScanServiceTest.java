package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.entity.InputMode;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.exception.StorageQuotaExceededException;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.service.impl.ScanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ScanServiceImpl scanService;

    private User mockUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = User.builder()
                .id(userId)
                .email("testuser@example.com")
                .password("encoded_pass")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .storageUsedBytes(1024L)
                .storageQuotaBytes(10737418240L) // 10 GB
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Create scan successfully with image input_mode (default)")
    void testCreateScanSuccessImageMode() {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("Antique Vase")
                .description("Ceramic vase 360 scan")
                .notes("Lighting box")
                .inputMode("image")
                .build();

        UUID scanId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(scanRepository.saveAndFlush(any(Scan.class))).thenAnswer(invocation -> {
            Scan scan = invocation.getArgument(0);
            scan.setId(scanId);
            scan.setCreatedAt(Instant.now());
            scan.setUpdatedAt(Instant.now());
            return scan;
        });

        ScanResponse response = scanService.createScan(request, userId);

        assertNotNull(response);
        assertEquals(scanId, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals("Antique Vase", response.getName());
        assertEquals("Ceramic vase 360 scan", response.getDescription());
        assertEquals("Lighting box", response.getNotes());
        assertEquals("image", response.getInputMode());
        assertEquals("draft", response.getStatus());
        assertEquals(0, response.getFileCount());
        assertEquals(0L, response.getTotalSizeBytes());
        assertNotNull(response.getCreatedAt());

        verify(userRepository, times(1)).findById(userId);
        verify(scanRepository, times(1)).saveAndFlush(any(Scan.class));
    }

    @Test
    @DisplayName("Create scan successfully with video input_mode")
    void testCreateScanSuccessVideoMode() {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("Shoe Scan")
                .inputMode("video")
                .build();

        UUID scanId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(scanRepository.saveAndFlush(any(Scan.class))).thenAnswer(invocation -> {
            Scan scan = invocation.getArgument(0);
            scan.setId(scanId);
            scan.setCreatedAt(Instant.now());
            scan.setUpdatedAt(Instant.now());
            return scan;
        });

        ScanResponse response = scanService.createScan(request, userId);

        assertNotNull(response);
        assertEquals(scanId, response.getId());
        assertEquals("Shoe Scan", response.getName());
        assertEquals("video", response.getInputMode());
        assertEquals("draft", response.getStatus());
        verify(scanRepository, times(1)).saveAndFlush(any(Scan.class));
    }

    @Test
    @DisplayName("Create scan rejects when user storage quota exceeded -> StorageQuotaExceededException")
    void testCreateScanStorageQuotaExceeded() {
        // storageUsedBytes >= storageQuotaBytes
        mockUser.setStorageUsedBytes(10737418240L);
        mockUser.setStorageQuotaBytes(10737418240L);

        CreateScanRequest request = CreateScanRequest.builder()
                .name("New Scan")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        StorageQuotaExceededException exception = assertThrows(
                StorageQuotaExceededException.class,
                () -> scanService.createScan(request, userId)
        );

        assertEquals("Xoá scan cũ để tiếp tục", exception.getMessage());
        verify(userRepository, times(1)).findById(userId);
        verify(scanRepository, never()).save(any(Scan.class));
    }

    @Test
    @DisplayName("Create scan rejects when user not found -> ResourceNotFoundException")
    void testCreateScanUserNotFound() {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("Scan Name")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.createScan(request, userId)
        );

        verify(scanRepository, never()).save(any(Scan.class));
    }
}

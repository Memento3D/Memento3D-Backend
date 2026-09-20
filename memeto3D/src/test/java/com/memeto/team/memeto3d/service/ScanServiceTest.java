package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanListResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
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

    @Test
    @DisplayName("Get scan by ID: Owner accesses own scan successfully")
    void testGetScanByIdSuccessOwner() {
        UUID scanId = UUID.randomUUID();
        java.util.Map<String, Object> deviceInfo = java.util.Map.of("model", "iPhone 15 Pro", "os", "iOS 17.2");
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Antique Vase")
                .description("Detailed description")
                .notes("Turntable 360")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .fileCount(45)
                .totalSizeBytes(1048576L)
                .deviceInfo(deviceInfo)
                .thumbnailPath("/storage/thumbnails/vase.jpg")
                .duplicatedFrom(null)
    @DisplayName("getScans returns user scans without status filter, sorted by created_at DESC by default")
    void testGetScansWithoutFilter() {
        Scan scan = Scan.builder()
                .id(UUID.randomUUID())
                .user(mockUser)
                .name("Scan 1")
                .status(ScanStatus.draft)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));

        ScanResponse response = scanService.getScanById(scanId, userId, Role.ROLE_USER);

        assertNotNull(response);
        assertEquals(scanId, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals("Antique Vase", response.getName());
        assertEquals("Detailed description", response.getDescription());
        assertEquals("Turntable 360", response.getNotes());
        assertEquals("image", response.getInputMode());
        assertEquals("draft", response.getStatus());
        assertEquals(45, response.getFileCount());
        assertEquals(1048576L, response.getTotalSizeBytes());
        assertEquals("/storage/thumbnails/vase.jpg", response.getThumbnailPath());
        assertEquals(deviceInfo, response.getDeviceInfo());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());

        verify(scanRepository, times(1)).findByIdAndDeletedAtIsNull(scanId);
    }

    @Test
    @DisplayName("Get scan by ID: Admin accesses another user's scan successfully")
    void testGetScanByIdSuccessAdmin() {
        UUID scanId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser) // owned by mockUser (userId)
                .name("User B's Scan")
                .inputMode(InputMode.video)
                .status(ScanStatus.processing)
                .fileCount(10)
                .totalSizeBytes(5000000L)
        Page<Scan> page = new PageImpl<>(List.of(scan));
        when(scanRepository.findByUserIdAndDeletedAtIsNull(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        ScanListResponse response = scanService.getScans(userId, null, 1, 20, null);

        assertNotNull(response);
        assertEquals(1, response.getScans().size());
        assertEquals(1, response.getItems().size());
        assertEquals(1L, response.getTotalCount());
        assertEquals(1, response.getPage());
        assertEquals(20, response.getLimit());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(scanRepository).findByUserIdAndDeletedAtIsNull(eq(userId), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    @DisplayName("getScans filters by status correctly when provided")
    void testGetScansWithStatusFilter() {
        Scan scan = Scan.builder()
                .id(UUID.randomUUID())
                .user(mockUser)
                .name("Scan 2")
                .status(ScanStatus.ready)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));

        ScanResponse response = scanService.getScanById(scanId, adminId, Role.ROLE_ADMIN);

        assertNotNull(response);
        assertEquals(scanId, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals("User B's Scan", response.getName());
        verify(scanRepository, times(1)).findByIdAndDeletedAtIsNull(scanId);
    }

    @Test
    @DisplayName("Get scan by ID: Operator accesses another user's scan successfully")
    void testGetScanByIdSuccessOperator() {
        UUID scanId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("User B's Scan")
                .inputMode(InputMode.image)
                .status(ScanStatus.ready)
                .fileCount(20)
                .totalSizeBytes(2000000L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));

        ScanResponse response = scanService.getScanById(scanId, operatorId, Role.ROLE_OPERATOR);

        assertNotNull(response);
        assertEquals(scanId, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals("User B's Scan", response.getName());
        verify(scanRepository, times(1)).findByIdAndDeletedAtIsNull(scanId);
    }

    @Test
    @DisplayName("Get scan by ID: Non-existent scan throws ResourceNotFoundException (404)")
    void testGetScanByIdNotFoundNonExistent() {
        UUID nonExistentId = UUID.randomUUID();
        when(scanRepository.findByIdAndDeletedAtIsNull(nonExistentId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.getScanById(nonExistentId, userId, Role.ROLE_USER)
        );

        assertTrue(ex.getMessage().contains(nonExistentId.toString()));
        verify(scanRepository, times(1)).findByIdAndDeletedAtIsNull(nonExistentId);
    }

    @Test
    @DisplayName("Get scan by ID: User A accessing User B's scan throws ResourceNotFoundException (IDOR protection 404)")
    void testGetScanByIdNotFoundAccessDeniedDifferentUser() {
        UUID scanId = UUID.randomUUID();
        UUID userAId = UUID.randomUUID();
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser) // owned by mockUser (userId != userAId)
                .name("User B's Private Scan")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.getScanById(scanId, userAId, Role.ROLE_USER)
        );

        assertTrue(ex.getMessage().contains(scanId.toString()));
        verify(scanRepository, times(1)).findByIdAndDeletedAtIsNull(scanId);
    }

    @Test
    @DisplayName("Get scan by ID: Scan with null user throws ResourceNotFoundException for regular user")
    void testGetScanByIdNotFoundScanWithNullUser() {
        UUID scanId = UUID.randomUUID();
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(null)
                .name("Orphaned Scan")
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));

        assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.getScanById(scanId, userId, Role.ROLE_USER)
        );
        Page<Scan> page = new PageImpl<>(List.of(scan));
        when(scanRepository.findByUserIdAndStatusAndDeletedAtIsNull(eq(userId), eq(ScanStatus.ready), any(Pageable.class)))
                .thenReturn(page);

        ScanListResponse response = scanService.getScans(userId, "ready", 1, 10, "created_at,desc");

        assertNotNull(response);
        assertEquals(1, response.getScans().size());
        assertEquals("ready", response.getScans().get(0).getStatus());

        verify(scanRepository, times(1))
                .findByUserIdAndStatusAndDeletedAtIsNull(eq(userId), eq(ScanStatus.ready), any(Pageable.class));
        verify(scanRepository, never())
                .findByUserIdAndDeletedAtIsNull(any(), any());
    }

    @Test
    @DisplayName("getScans ignores blank status and queries all scans")
    void testGetScansWithBlankStatus() {
        Page<Scan> emptyPage = new PageImpl<>(Collections.emptyList());
        when(scanRepository.findByUserIdAndDeletedAtIsNull(eq(userId), any(Pageable.class)))
                .thenReturn(emptyPage);

        ScanListResponse response = scanService.getScans(userId, "   ", 1, 20, "");

        assertNotNull(response);
        assertEquals(0, response.getScans().size());
        verify(scanRepository).findByUserIdAndDeletedAtIsNull(eq(userId), any(Pageable.class));
    }

    @Test
    @DisplayName("getScans throws IllegalArgumentException when status is invalid")
    void testGetScansWithInvalidStatus() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(userId, "not_a_valid_status", 1, 20, null)
        );
        assertTrue(ex.getMessage().contains("Invalid status: not_a_valid_status"));
    }

    @Test
    @DisplayName("getScans throws IllegalArgumentException when userId is null")
    void testGetScansNullUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(null, null, 1, 20, null)
        );
    }

    @Test
    @DisplayName("getScans throws IllegalArgumentException when page < 1")
    void testGetScansInvalidPage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(userId, null, 0, 20, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(userId, null, -1, 20, null)
        );
    }

    @Test
    @DisplayName("getScans throws IllegalArgumentException when limit is out of range [1, 100]")
    void testGetScansInvalidLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(userId, null, 1, 0, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(userId, null, 1, 101, null)
        );
    }

    @Test
    @DisplayName("getScans parses various valid sort options")
    void testGetScansVariousSorts() {
        Page<Scan> emptyPage = new PageImpl<>(Collections.emptyList());
        when(scanRepository.findByUserIdAndDeletedAtIsNull(eq(userId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // 1. name,asc
        scanService.getScans(userId, null, 1, 20, "name,asc");
        // 2. updated_at:desc
        scanService.getScans(userId, null, 1, 20, "updated_at:desc");
        // 3. file_count asc
        scanService.getScans(userId, null, 1, 20, "file_count asc");
        // 4. total_size_bytes,desc
        scanService.getScans(userId, null, 1, 20, "total_size_bytes,desc");
        // 5. status:asc
        scanService.getScans(userId, null, 1, 20, "status:asc");
        // 6. name (no direction -> defaults to DESC)
        scanService.getScans(userId, null, 1, 20, "name");

        verify(scanRepository, times(6)).findByUserIdAndDeletedAtIsNull(eq(userId), any(Pageable.class));
    }

    @Test
    @DisplayName("getScans throws IllegalArgumentException when sort field is invalid")
    void testGetScansInvalidSortField() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(userId, null, 1, 20, "disallowed_field,asc")
        );
        assertTrue(ex.getMessage().contains("Invalid sort field"));
    }

    @Test
    @DisplayName("getScans throws IllegalArgumentException when sort direction is invalid")
    void testGetScansInvalidSortDirection() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> scanService.getScans(userId, null, 1, 20, "name,sideways")
        );
        assertTrue(ex.getMessage().contains("Invalid sort direction: sideways"));
    }
}

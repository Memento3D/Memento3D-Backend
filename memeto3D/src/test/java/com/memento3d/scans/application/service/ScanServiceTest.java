package com.memento3d.scans.application.service;

import com.memento3d.scans.api.request.CreateScanRequest;
import com.memento3d.scans.api.response.ScanListResponse;
import com.memento3d.scans.api.response.ScanResponse;
import com.memento3d.scans.api.response.ScanStatusResponse;
import com.memento3d.scans.api.request.UpdateScanRequest;
import com.memento3d.scans.domain.model.InputMode;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanStatus;
import com.memento3d.identity.domain.model.User;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.scans.domain.exception.ScanConflictException;
import com.memento3d.assets.domain.exception.StorageQuotaExceededException;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.scans.application.service.impl.ScanServiceImpl;
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

    @Mock
    private com.memento3d.quality.infrastructure.persistence.repository.QualityCheckRunRepository qualityCheckRunRepository;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private com.memento3d.scans.application.service.ScanStatusCacheService scanStatusCacheService;

    @Mock
    private com.memento3d.scans.application.service.ScanAccessGuard scanAccessGuard;

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

        assertTrue(exception.getMessage().contains("scan") || exception.getMessage().contains("Xo"));
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
    }

    @Test
    @DisplayName("Update scan: Successfully updates name, description, and notes")
    void testUpdateScanSuccessAllFields() {
        UUID scanId = UUID.randomUUID();
        Instant originalUpdatedAt = Instant.now().minusSeconds(60);
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Old Name")
                .description("Old Description")
                .notes("Old Notes")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .createdAt(Instant.now().minusSeconds(120))
                .updatedAt(originalUpdatedAt)
                .build();

        UpdateScanRequest request = UpdateScanRequest.builder()
                .name("New Name")
                .description("New Description")
                .notes("New Notes")
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));
        when(scanRepository.saveAndFlush(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScanResponse response = scanService.updateScan(scanId, request, userId, Role.ROLE_USER);

        assertNotNull(response);
        assertEquals("New Name", response.getName());
        assertEquals("New Description", response.getDescription());
        assertEquals("New Notes", response.getNotes());
        assertEquals("draft", response.getStatus());

        ArgumentCaptor<Scan> captor = ArgumentCaptor.forClass(Scan.class);
        verify(scanRepository, times(1)).saveAndFlush(captor.capture());
        Scan saved = captor.getValue();
        assertEquals("New Name", saved.getName());
        assertEquals("New Description", saved.getDescription());
        assertEquals("New Notes", saved.getNotes());
        assertNotEquals(originalUpdatedAt, saved.getUpdatedAt());
    }

    @Test
    @DisplayName("Update scan: Partial update only changes non-null fields")
    void testUpdateScanPartialFields() {
        UUID scanId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Original Name")
                .description("Original Description")
                .notes("Original Notes")
                .status(ScanStatus.uploaded)
                .build();

        UpdateScanRequest onlyName = UpdateScanRequest.builder().name("Updated Only Name").build();
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));
        when(scanRepository.saveAndFlush(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScanResponse res1 = scanService.updateScan(scanId, onlyName, userId, Role.ROLE_USER);
        assertEquals("Updated Only Name", res1.getName());
        assertEquals("Original Description", res1.getDescription());
        assertEquals("Original Notes", res1.getNotes());

        UpdateScanRequest onlyDesc = UpdateScanRequest.builder().description("Updated Only Desc").build();
        ScanResponse res2 = scanService.updateScan(scanId, onlyDesc, userId, Role.ROLE_USER);
        assertEquals("Updated Only Name", res2.getName());
        assertEquals("Updated Only Desc", res2.getDescription());
        assertEquals("Original Notes", res2.getNotes());

        UpdateScanRequest onlyNotes = UpdateScanRequest.builder().notes("Updated Only Notes").build();
        ScanResponse res3 = scanService.updateScan(scanId, onlyNotes, userId, Role.ROLE_USER);
        assertEquals("Updated Only Name", res3.getName());
        assertEquals("Updated Only Desc", res3.getDescription());
        assertEquals("Updated Only Notes", res3.getNotes());
    }

    @Test
    @DisplayName("Update scan: Null request body leaves fields unchanged and updates updatedAt")
    void testUpdateScanNullRequest() {
        UUID scanId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Keep Name")
                .status(ScanStatus.draft)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));
        when(scanRepository.saveAndFlush(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScanResponse response = scanService.updateScan(scanId, null, userId, Role.ROLE_USER);
        assertEquals("Keep Name", response.getName());
        verify(scanRepository, times(1)).saveAndFlush(any(Scan.class));
    }

    @Test
    @DisplayName("Update scan: Empty or blank name throws IllegalArgumentException")
    void testUpdateScanEmptyNameThrowsException() {
        UUID scanId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Current Name")
                .status(ScanStatus.draft)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));

        UpdateScanRequest blankReq = UpdateScanRequest.builder().name("   ").build();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> scanService.updateScan(scanId, blankReq, userId, Role.ROLE_USER)
        );
        assertEquals("Scan name cannot be empty", ex.getMessage());
        verify(scanRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Update scan: Name exceeding 255 characters throws IllegalArgumentException")
    void testUpdateScanNameTooLongThrowsException() {
        UUID scanId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Current Name")
                .status(ScanStatus.draft)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));

        UpdateScanRequest longReq = UpdateScanRequest.builder().name("a".repeat(256)).build();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> scanService.updateScan(scanId, longReq, userId, Role.ROLE_USER)
        );
        assertEquals("Name must not exceed 255 characters", ex.getMessage());
        verify(scanRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Update scan: Non-existent scan throws ResourceNotFoundException")
    void testUpdateScanNotFoundThrowsException() {
        UUID scanId = UUID.randomUUID();
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.empty());

        UpdateScanRequest req = UpdateScanRequest.builder().name("Valid Name").build();
        assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.updateScan(scanId, req, userId, Role.ROLE_USER)
        );
        verify(scanRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Update scan: User A attempting to update User B's scan throws ResourceNotFoundException (IDOR)")
    void testUpdateScanAccessDeniedIDOR() {
        UUID scanId = UUID.randomUUID();
        UUID userBId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser) // owned by mockUser (userId != userBId)
                .name("Scan B")
                .status(ScanStatus.draft)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));

        UpdateScanRequest req = UpdateScanRequest.builder().name("Hacked Name").build();
        assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.updateScan(scanId, req, userBId, Role.ROLE_USER)
        );
        verify(scanRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Update scan: Scan with null user throws ResourceNotFoundException for regular user")
    void testUpdateScanScanWithNullUserThrowsNotFound() {
        UUID scanId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(null)
                .name("Orphan Scan")
                .status(ScanStatus.draft)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));

        UpdateScanRequest req = UpdateScanRequest.builder().name("New Name").build();
        assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.updateScan(scanId, req, userId, Role.ROLE_USER)
        );
    }

    @Test
    @DisplayName("Update scan: Admin or Operator can update another user's scan")
    void testUpdateScanPrivilegedRoles() {
        UUID scanId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Original Name")
                .status(ScanStatus.draft)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));
        when(scanRepository.saveAndFlush(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateScanRequest adminReq = UpdateScanRequest.builder().name("Admin Updated Name").build();
        ScanResponse adminRes = scanService.updateScan(scanId, adminReq, otherUserId, Role.ROLE_ADMIN);
        assertEquals("Admin Updated Name", adminRes.getName());

        UpdateScanRequest operatorReq = UpdateScanRequest.builder().name("Operator Updated Name").build();
        ScanResponse operatorRes = scanService.updateScan(scanId, operatorReq, otherUserId, Role.ROLE_OPERATOR);
        assertEquals("Operator Updated Name", operatorRes.getName());
    }

    @Test
    @DisplayName("Update scan: Status is 'uploading' throws ScanConflictException (409)")
    void testUpdateScanConflictStatusUploading() {
        UUID scanId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Uploading Scan")
                .status(ScanStatus.uploading)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));

        UpdateScanRequest req = UpdateScanRequest.builder().name("New Name").build();
        ScanConflictException ex = assertThrows(
                ScanConflictException.class,
                () -> scanService.updateScan(scanId, req, userId, Role.ROLE_USER)
        );
        assertTrue(ex.getMessage().contains("uploading"));
        verify(scanRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Update scan: Status is 'processing' throws ScanConflictException (409)")
    void testUpdateScanConflictStatusProcessing() {
        UUID scanId = UUID.randomUUID();
        Scan existingScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Processing Scan")
                .status(ScanStatus.processing)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(existingScan));

        UpdateScanRequest req = UpdateScanRequest.builder().name("New Name").build();
        ScanConflictException ex = assertThrows(
                ScanConflictException.class,
                () -> scanService.updateScan(scanId, req, userId, Role.ROLE_USER)
        );
        assertTrue(ex.getMessage().contains("processing"));
        verify(scanRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getQualityCheckRuns returns list of runs successfully")
    void testGetQualityCheckRunsSuccess() {
        UUID scanId = UUID.randomUUID();
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .build();

        com.memento3d.quality.domain.model.QualityCheckRun run1 = com.memento3d.quality.domain.model.QualityCheckRun.builder()
                .id(UUID.randomUUID())
                .scan(mockScan)
                .runNumber(2)
                .status("completed")
                .overallScore(new java.math.BigDecimal("90.5"))
                .scoreCategory("green")
                .createdAt(Instant.now())
                .build();

        com.memento3d.quality.domain.model.QualityCheckRun run2 = com.memento3d.quality.domain.model.QualityCheckRun.builder()
                .id(UUID.randomUUID())
                .scan(mockScan)
                .runNumber(1)
                .status("completed")
                .overallScore(new java.math.BigDecimal("70.0"))
                .scoreCategory("yellow")
                .createdAt(Instant.now())
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));
        when(qualityCheckRunRepository.findByScanIdOrderByRunNumberDesc(scanId))
                .thenReturn(List.of(run1, run2));

        List<com.memento3d.quality.api.response.QualityCheckRunSummaryResponse> result = scanService.getQualityCheckRuns(scanId, userId, Role.ROLE_USER);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getRunNumber());
        assertEquals("completed", result.get(0).getStatus());
        assertEquals(new java.math.BigDecimal("90.5"), result.get(0).getOverallScore());
        assertEquals("green", result.get(0).getScoreCategory());
        
        assertEquals(1, result.get(1).getRunNumber());
    }

    @Test
    @DisplayName("getQualityCheckRuns returns empty list if no runs")
    void testGetQualityCheckRunsEmpty() {
        UUID scanId = UUID.randomUUID();
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));
        when(qualityCheckRunRepository.findByScanIdOrderByRunNumberDesc(scanId))
                .thenReturn(Collections.emptyList());

        List<com.memento3d.quality.api.response.QualityCheckRunSummaryResponse> result = scanService.getQualityCheckRuns(scanId, userId, Role.ROLE_USER);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getQualityCheckRuns throws ResourceNotFoundException if scan not found")
    void testGetQualityCheckRunsNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(scanRepository.findByIdAndDeletedAtIsNull(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.getQualityCheckRuns(nonExistentId, userId, Role.ROLE_USER)
        );
        verify(qualityCheckRunRepository, never()).findByScanIdOrderByRunNumberDesc(any());
    }

    @Test
    @DisplayName("getQualityCheckRuns throws ResourceNotFoundException for non-owner without privilege (IDOR)")
    void testGetQualityCheckRunsAccessDeniedIDOR() {
        UUID scanId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Scan mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser) // owned by mockUser (userId)
                .build();

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(mockScan));
        doThrow(new ResourceNotFoundException("Scan not found with ID: " + scanId))
                .when(scanAccessGuard).requireAccess(mockScan, otherUserId, Role.ROLE_USER);

        assertThrows(
                ResourceNotFoundException.class,
                () -> scanService.getQualityCheckRuns(scanId, otherUserId, Role.ROLE_USER)
        );
        verify(qualityCheckRunRepository, never()).findByScanIdOrderByRunNumberDesc(any());
    }

    // ---------- overrideQualityRun ----------

    private Scan overrideScan(UUID scanId, ScanStatus status) {
        return Scan.builder().id(scanId).user(mockUser).status(status).build();
    }

    private com.memento3d.quality.domain.model.QualityCheckRun overrideRun(Scan scan, UUID runId, String category) {
        return com.memento3d.quality.domain.model.QualityCheckRun.builder()
                .id(runId).scan(scan).status("completed").scoreCategory(category).build();
    }

    @Test
    @DisplayName("override amber -> scan ready, run marked, audit written")
    void testOverrideAmberSuccess() {
        UUID scanId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Scan scan = overrideScan(scanId, ScanStatus.quality_check);
        com.memento3d.quality.domain.model.QualityCheckRun run = overrideRun(scan, runId, "amber");
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));

        com.memento3d.quality.api.response.QualityOverrideResponse res =
                scanService.overrideQualityRun(scanId, runId, userId, Role.ROLE_USER);

        assertEquals(ScanStatus.ready, scan.getStatus());
        assertEquals("ready", res.getScanStatus());
        assertNotNull(run.getOverriddenAt());
        assertEquals(userId, run.getOverriddenBy());
        verify(jdbcTemplate, times(1)).update(contains("audit_logs"),
                eq(userId), eq("QUALITY_OVERRIDE"), eq("quality_check_run"), eq(runId), anyString());
    }

    @Test
    @DisplayName("override red -> 403 exception, nothing changes")
    void testOverrideRedForbidden() {
        UUID scanId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Scan scan = overrideScan(scanId, ScanStatus.quality_check);
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(overrideRun(scan, runId, "red")));

        assertThrows(com.memento3d.quality.domain.exception.QualityOverrideForbiddenException.class,
                () -> scanService.overrideQualityRun(scanId, runId, userId, Role.ROLE_USER));
        assertEquals(ScanStatus.quality_check, scan.getStatus());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("override green -> IllegalArgumentException (400)")
    void testOverrideGreenBadRequest() {
        UUID scanId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Scan scan = overrideScan(scanId, ScanStatus.quality_check);
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(overrideRun(scan, runId, "green")));

        assertThrows(IllegalArgumentException.class,
                () -> scanService.overrideQualityRun(scanId, runId, userId, Role.ROLE_USER));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("override twice -> idempotent, single audit entry")
    void testOverrideIdempotent() {
        UUID scanId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Scan scan = overrideScan(scanId, ScanStatus.quality_check);
        com.memento3d.quality.domain.model.QualityCheckRun run = overrideRun(scan, runId, "amber");
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(run));

        scanService.overrideQualityRun(scanId, runId, userId, Role.ROLE_USER);
        Instant first = run.getOverriddenAt();
        com.memento3d.quality.api.response.QualityOverrideResponse second =
                scanService.overrideQualityRun(scanId, runId, userId, Role.ROLE_USER);

        assertEquals(first, second.getOverriddenAt());
        assertEquals("ready", second.getScanStatus());
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("override run of another scan -> 404")
    void testOverrideRunWrongScan() {
        UUID scanId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Scan scan = overrideScan(scanId, ScanStatus.quality_check);
        Scan other = overrideScan(UUID.randomUUID(), ScanStatus.quality_check);
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(overrideRun(other, runId, "amber")));

        assertThrows(ResourceNotFoundException.class,
                () -> scanService.overrideQualityRun(scanId, runId, userId, Role.ROLE_USER));
    }

    @Test
    @DisplayName("override by non-owner -> 404 (IDOR)")
    void testOverrideIdor() {
        UUID scanId = UUID.randomUUID();
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId))
                .thenReturn(Optional.of(overrideScan(scanId, ScanStatus.quality_check)));

        assertThrows(ResourceNotFoundException.class,
                () -> scanService.overrideQualityRun(scanId, UUID.randomUUID(), UUID.randomUUID(), Role.ROLE_USER));
    }

    @Test
    @DisplayName("override while scan processing -> ScanConflictException")
    void testOverrideProcessingConflict() {
        UUID scanId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Scan scan = overrideScan(scanId, ScanStatus.processing);
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(qualityCheckRunRepository.findById(runId)).thenReturn(Optional.of(overrideRun(scan, runId, "amber")));

        assertThrows(ScanConflictException.class,
                () -> scanService.overrideQualityRun(scanId, runId, userId, Role.ROLE_USER));
    }

    @Test
    @DisplayName("getScanStatus returns cached status details on hit")
    void testGetScanStatusCacheHit() {
        UUID scanId = UUID.randomUUID();
        Scan scan = Scan.builder().id(scanId).user(mockUser).status(ScanStatus.processing).build();

        ScanStatusResponse cachedResponse = ScanStatusResponse.builder()
                .scanId(scanId.toString())
                .status("PROCESSING")
                .progress(65)
                .message("Generating 3D model")
                .build();

        when(scanStatusCacheService.getScanStatusDetails(scanId)).thenReturn(Optional.of(cachedResponse));
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));

        ScanStatusResponse result = scanService.getScanStatus(scanId, userId, Role.ROLE_USER);

        assertEquals("PROCESSING", result.getStatus());
        assertEquals(65, result.getProgress());
        assertEquals("Generating 3D model", result.getMessage());
        verify(scanStatusCacheService, times(1)).getScanStatusDetails(scanId);
    }

    @Test
    @DisplayName("getScanStatus queries DB and caches status on cache miss")
    void testGetScanStatusCacheMiss() {
        UUID scanId = UUID.randomUUID();
        Scan scan = Scan.builder().id(scanId).user(mockUser).status(ScanStatus.draft).build();

        when(scanStatusCacheService.getScanStatusDetails(scanId)).thenReturn(Optional.empty());
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));

        ScanStatusResponse result = scanService.getScanStatus(scanId, userId, Role.ROLE_USER);

        assertEquals("CREATED", result.getStatus());
        assertEquals(0, result.getProgress());
        assertEquals("Draft scan session created", result.getMessage());
        verify(scanStatusCacheService, times(1)).getScanStatusDetails(scanId);
        verify(scanStatusCacheService, times(1)).cacheScanStatusDetails(scanId, "CREATED", 0, "Draft scan session created");
    }
}

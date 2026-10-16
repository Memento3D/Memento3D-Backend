package com.memento3d.scans.api;

import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.scans.domain.model.Scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memento3d.scans.api.request.CreateScanRequest;
import com.memento3d.scans.api.response.ScanImageListResponse;
import com.memento3d.scans.api.response.ScanImageResponse;
import com.memento3d.assets.api.response.ScanImageUploadResponse;
import com.memento3d.scans.api.response.ScanListResponse;
import com.memento3d.scans.api.response.ScanResponse;
import com.memento3d.scans.api.request.UpdateScanRequest;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.assets.domain.exception.FileSizeExceededException;
import com.memento3d.shared.api.GlobalExceptionHandler;
import com.memento3d.assets.domain.exception.InvalidFileFormatException;
import com.memento3d.scans.domain.exception.ScanConflictException;
import com.memento3d.assets.domain.exception.StorageQuotaExceededException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.scans.application.service.ScanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ScanControllerTest {

    @Mock
    private ScanService scanService;

    @InjectMocks
    private ScanController scanController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(scanController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testUserId = UUID.randomUUID();
        User testUser = User.builder()
                .id(testUserId)
                .email("user@example.com")
                .password("encoded_pass")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .storageUsedBytes(0L)
                .storageQuotaBytes(10737418240L)
                .build();
        mockUserDetails = new CustomUserDetails(testUser);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                mockUserDetails, null, mockUserDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Case 1: POST /api/v1/scans - Tạo thành công trả về 201 + object scan đầy đủ field")
    void testCreateScanSuccess() throws Exception {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("Vintage Camera")
                .description("Detailed 360 capture")
                .notes("Lighting box, 50 frames")
                .inputMode("image")
                .build();

        UUID scanId = UUID.randomUUID();
        ScanResponse mockResponse = ScanResponse.builder()
                .id(scanId)
                .userId(testUserId)
                .name("Vintage Camera")
                .description("Detailed 360 capture")
                .notes("Lighting box, 50 frames")
                .inputMode("image")
                .status("draft")
                .fileCount(0)
                .totalSizeBytes(0L)
                .thumbnailPath(null)
                .duplicatedFrom(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(scanService.createScan(any(CreateScanRequest.class), eq(testUserId)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(scanId.toString())))
                .andExpect(jsonPath("$.user_id", is(testUserId.toString())))
                .andExpect(jsonPath("$.name", is("Vintage Camera")))
                .andExpect(jsonPath("$.description", is("Detailed 360 capture")))
                .andExpect(jsonPath("$.notes", is("Lighting box, 50 frames")))
                .andExpect(jsonPath("$.input_mode", is("image")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.file_count", is(0)))
                .andExpect(jsonPath("$.total_size_bytes", is(0)))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()));

        verify(scanService, times(1)).createScan(any(CreateScanRequest.class), eq(testUserId));
    }

    @Test
    @DisplayName("POST /api/v1/scans - Tạo scan với payload cụ thể từ User Request")
    void testCreateScanWithUserPayload() throws Exception {
        String jsonPayload = """
                {
                  "name": "My 3D Object Scan",
                  "description": "Scan of a clay sculpture under diffuse lighting",
                  "notes": "Captured 45 photos with turntable",
                  "input_mode": "image"
                }
                """;

        UUID scanId = UUID.randomUUID();
        ScanResponse mockResponse = ScanResponse.builder()
                .id(scanId)
                .userId(testUserId)
                .name("My 3D Object Scan")
                .description("Scan of a clay sculpture under diffuse lighting")
                .notes("Captured 45 photos with turntable")
                .inputMode("image")
                .status("draft")
                .fileCount(0)
                .totalSizeBytes(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(scanService.createScan(any(CreateScanRequest.class), eq(testUserId)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(scanId.toString())))
                .andExpect(jsonPath("$.user_id", is(testUserId.toString())))
                .andExpect(jsonPath("$.name", is("My 3D Object Scan")))
                .andExpect(jsonPath("$.description", is("Scan of a clay sculpture under diffuse lighting")))
                .andExpect(jsonPath("$.notes", is("Captured 45 photos with turntable")))
                .andExpect(jsonPath("$.input_mode", is("image")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.file_count", is(0)))
                .andExpect(jsonPath("$.total_size_bytes", is(0)))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()));

        verify(scanService, times(1)).createScan(any(CreateScanRequest.class), eq(testUserId));
    }

    @Test
    @DisplayName("Case 1b: POST /api/v1/scans - Không gửi input_mode mặc định là 'image'")
    void testCreateScanDefaultInputMode() throws Exception {
        String jsonPayload = "{\"name\": \"My Scan Session\"}";

        UUID scanId = UUID.randomUUID();
        ScanResponse mockResponse = ScanResponse.builder()
                .id(scanId)
                .userId(testUserId)
                .name("My Scan Session")
                .inputMode("image")
                .status("draft")
                .fileCount(0)
                .totalSizeBytes(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(scanService.createScan(any(CreateScanRequest.class), eq(testUserId)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(scanId.toString())))
                .andExpect(jsonPath("$.name", is("My Scan Session")))
                .andExpect(jsonPath("$.input_mode", is("image")));
    }

    @Test
    @DisplayName("Case 2: POST /api/v1/scans - name rỗng -> 400 với message rõ ràng")
    void testCreateScanEmptyName() throws Exception {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("")
                .inputMode("image")
                .build();

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.name", is("Name is required")));

        verify(scanService, never()).createScan(any(), any());
    }

    @Test
    @DisplayName("Case 2b: POST /api/v1/scans - name chỉ chứa khoảng trắng -> 400")
    void testCreateScanBlankName() throws Exception {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("    ")
                .inputMode("image")
                .build();

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.name", is("Name is required")));

        verify(scanService, never()).createScan(any(), any());
    }

    @Test
    @DisplayName("Case 3: POST /api/v1/scans - Vượt quota -> 403 với message 'Xoá scan cũ để tiếp tục'")
    void testCreateScanStorageQuotaExceeded() throws Exception {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("New Scan")
                .inputMode("image")
                .build();

        when(scanService.createScan(any(CreateScanRequest.class), eq(testUserId)))
                .thenThrow(new StorageQuotaExceededException("Xoá scan cũ để tiếp tục"));

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", is("Xoá scan cũ để tiếp tục")));

        verify(scanService, times(1)).createScan(any(CreateScanRequest.class), eq(testUserId));
    }

    @Test
    @DisplayName("Case 4: POST /api/v1/scans - input_mode không hợp lệ -> 400 (CHECK constraint image/video)")
    void testCreateScanInvalidInputMode() throws Exception {
        String jsonPayload = "{\"name\": \"Valid Name\", \"input_mode\": \"invalid_mode\"}";

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.inputMode", is("Input mode must be 'image' or 'video'")));

        verify(scanService, never()).createScan(any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/scans - Lấy danh sách thành công với tham số mặc định")
    void testGetScansDefaultParams() throws Exception {
        ScanResponse scan1 = ScanResponse.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .name("Scan 1")
                .status("draft")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ScanListResponse mockListResponse = ScanListResponse.builder()
                .scans(List.of(scan1))
                .totalCount(1L)
                .page(1)
                .limit(20)
                .totalPages(1)
                .build();

        when(scanService.getScans(eq(testUserId), isNull(), eq(1), eq(20), isNull()))
                .thenReturn(mockListResponse);

        mockMvc.perform(get("/api/v1/scans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scans", hasSize(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total_count", is(1)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.limit", is(20)))
                .andExpect(jsonPath("$.total_pages", is(1)))
                .andExpect(jsonPath("$.scans[0].name", is("Scan 1")));

        verify(scanService, times(1)).getScans(eq(testUserId), isNull(), eq(1), eq(20), isNull());
    }

    @Test
    @DisplayName("GET /api/v1/scans - Lấy danh sách với custom status, page, limit, sort")
    void testGetScansWithCustomParams() throws Exception {
        ScanListResponse mockListResponse = ScanListResponse.builder()
                .scans(Collections.emptyList())
                .totalCount(0L)
                .page(2)
                .limit(10)
                .totalPages(0)
                .build();

        when(scanService.getScans(eq(testUserId), eq("draft"), eq(2), eq(10), eq("name:asc")))
                .thenReturn(mockListResponse);

        mockMvc.perform(get("/api/v1/scans")
                        .param("status", "draft")
                        .param("page", "2")
                        .param("limit", "10")
                        .param("sort", "name:asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scans", hasSize(0)))
                .andExpect(jsonPath("$.total_count", is(0)))
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.limit", is(10)));

        verify(scanService, times(1)).getScans(eq(testUserId), eq("draft"), eq(2), eq(10), eq("name:asc"));
    }

    @Test
    @DisplayName("GET /api/v1/scans - Status không hợp lệ -> 400 Bad Request")
    void testGetScansInvalidStatus() throws Exception {
        when(scanService.getScans(eq(testUserId), eq("unknown_status"), eq(1), eq(20), isNull()))
                .thenThrow(new IllegalArgumentException("Invalid status: unknown_status"));

        mockMvc.perform(get("/api/v1/scans")
                        .param("status", "unknown_status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Invalid status: unknown_status")));

        verify(scanService, times(1)).getScans(eq(testUserId), eq("unknown_status"), eq(1), eq(20), isNull());
    }

    @Test
    @DisplayName("GET /api/v1/scans - Sort field không hợp lệ -> 400 Bad Request")
    void testGetScansInvalidSortField() throws Exception {
        when(scanService.getScans(eq(testUserId), isNull(), eq(1), eq(20), eq("invalid_field:asc")))
                .thenThrow(new IllegalArgumentException("Invalid sort field: invalid_field"));

        mockMvc.perform(get("/api/v1/scans")
                        .param("sort", "invalid_field:asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Invalid sort field: invalid_field")));

        verify(scanService, times(1)).getScans(eq(testUserId), isNull(), eq(1), eq(20), eq("invalid_field:asc"));
    }

    @Test
    @DisplayName("GET /api/v1/scans - Không có authentication -> 401 Unauthorized")
    void testGetScansUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/scans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("No authenticated user in security context")));

        verify(scanService, never()).getScans(any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("ScanController - getAuthenticatedUserId resolves from SecurityContext when userDetails is null")
    void testGetAuthenticatedUserIdFallbackToSecurityContext() {
        ScanListResponse mockListResponse = ScanListResponse.builder()
                .scans(Collections.emptyList())
                .totalCount(0L)
                .build();
        when(scanService.getScans(eq(testUserId), isNull(), eq(1), eq(20), isNull()))
                .thenReturn(mockListResponse);

        // Call method directly with userDetails = null
        var response = scanController.getScans(null, 1, 20, null, null);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("ScanController - getAuthenticatedUserId throws when unauthenticated")
    void testGetAuthenticatedUserIdUnauthenticated() {
        UsernamePasswordAuthenticationToken unauth =
                new UsernamePasswordAuthenticationToken("user", "pass"); // isAuthenticated() == false
        SecurityContextHolder.getContext().setAuthentication(unauth);

        com.memento3d.identity.domain.exception.InvalidTokenException ex = assertThrows(
                com.memento3d.identity.domain.exception.InvalidTokenException.class,
                () -> scanController.getScans(null, 1, 20, null, null)
        );
        assertEquals("No authenticated user in security context", ex.getMessage());
    }

    @Test
    @DisplayName("ScanController - getAuthenticatedUserId throws when anonymousUser")
    void testGetAuthenticatedUserIdAnonymousUser() {
        UsernamePasswordAuthenticationToken anon =
                new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(anon);

        com.memento3d.identity.domain.exception.InvalidTokenException ex = assertThrows(
                com.memento3d.identity.domain.exception.InvalidTokenException.class,
                () -> scanController.getScans(null, 1, 20, null, null)
        );
        assertEquals("No authenticated user in security context", ex.getMessage());
    }

    @Test
    @DisplayName("ScanController - getAuthenticatedUserId throws when principal is unexpected type")
    void testGetAuthenticatedUserIdUnexpectedPrincipal() {
        UsernamePasswordAuthenticationToken other =
                new UsernamePasswordAuthenticationToken("otherPrincipalString", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(other);

        com.memento3d.identity.domain.exception.InvalidTokenException ex = assertThrows(
                com.memento3d.identity.domain.exception.InvalidTokenException.class,
                () -> scanController.getScans(null, 1, 20, null, null)
        );
        assertEquals("Invalid user details in security context", ex.getMessage());
    }

    @Test
    @DisplayName("ScanController - getAuthenticatedUserId handles userDetails with null ID by falling back to SecurityContext")
    void testGetAuthenticatedUserIdUserDetailsWithNullId() {
        CustomUserDetails nullIdUser = mock(CustomUserDetails.class);
        when(nullIdUser.getId()).thenReturn(null);

        ScanListResponse mockListResponse = ScanListResponse.builder().scans(Collections.emptyList()).build();
        when(scanService.getScans(eq(testUserId), isNull(), eq(1), eq(20), isNull()))
                .thenReturn(mockListResponse);

        var response = scanController.getScans(null, 1, 20, null, nullIdUser);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/v1/scans/{id} - Lấy chi tiết scan thành công trả về 200 + đầy đủ fields")
    void testGetScanByIdSuccess() throws Exception {
        UUID scanId = UUID.randomUUID();
        java.util.Map<String, Object> deviceInfo = java.util.Map.of("model", "iPhone 15 Pro", "os", "iOS 17.2");
        ScanResponse mockResponse = ScanResponse.builder()
                .id(scanId)
                .userId(testUserId)
                .name("Antique Vase")
                .description("Detailed description")
                .notes("Turntable 360")
                .inputMode("image")
                .status("draft")
                .fileCount(45)
                .totalSizeBytes(1048576L)
                .thumbnailPath("/storage/thumbnails/vase.jpg")
                .deviceInfo(deviceInfo)
                .duplicatedFrom(null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(scanService.getScanById(eq(scanId), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/scans/{id}", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(scanId.toString())))
                .andExpect(jsonPath("$.user_id", is(testUserId.toString())))
                .andExpect(jsonPath("$.name", is("Antique Vase")))
                .andExpect(jsonPath("$.description", is("Detailed description")))
                .andExpect(jsonPath("$.notes", is("Turntable 360")))
                .andExpect(jsonPath("$.input_mode", is("image")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.file_count", is(45)))
                .andExpect(jsonPath("$.total_size_bytes", is(1048576)))
                .andExpect(jsonPath("$.thumbnail_path", is("/storage/thumbnails/vase.jpg")))
                .andExpect(jsonPath("$.device_info.model", is("iPhone 15 Pro")))
                .andExpect(jsonPath("$.device_info.os", is("iOS 17.2")))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()));

        verify(scanService, times(1)).getScanById(eq(scanId), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("GET /api/v1/scans/{id} - Scan không tồn tại hoặc không thuộc quyền sở hữu -> 404")
    void testGetScanByIdNotFound() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.getScanById(eq(scanId), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new com.memento3d.shared.exception.ResourceNotFoundException("Scan not found with ID: " + scanId));

        mockMvc.perform(get("/api/v1/scans/{id}", scanId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Scan not found with ID: " + scanId)));

        verify(scanService, times(1)).getScanById(eq(scanId), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("GET /api/v1/scans/{id} - Authentication null trong context -> 401 Unauthorized")
    void testGetScanByIdUnauthenticatedContext() throws Exception {
        UUID scanId = UUID.randomUUID();
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/scans/{id}", scanId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("No authenticated user in security context")));

        verify(scanService, never()).getScanById(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/scans/{id} - Principal không hợp lệ trong context -> 401 Unauthorized")
    void testGetScanByIdInvalidPrincipalContext() throws Exception {
        UUID scanId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken invalidAuth = new UsernamePasswordAuthenticationToken(
                "invalid_user_string", null, java.util.Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(invalidAuth);

        mockMvc.perform(get("/api/v1/scans/{id}", scanId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid user details in security context")));

        verify(scanService, never()).getScanById(any(), any(), any());
    }

    @Test
    @DisplayName("ScanController helper - userDetails null nhưng context có CustomUserDetails -> trích xuất thành công")
    void testGetScanByIdFallbackToSecurityContext() {
        UUID scanId = UUID.randomUUID();
        when(scanService.getScanById(scanId, testUserId, Role.ROLE_USER))
                .thenReturn(ScanResponse.builder().id(scanId).build());

        var response = scanController.getScanById(scanId, null);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(scanService, times(1)).getScanById(scanId, testUserId, Role.ROLE_USER);
    }

    @Test
    @DisplayName("ScanController helper - userDetails có id null -> fallback vào SecurityContext")
    void testGetScanByIdUserDetailsWithNullId() {
        UUID scanId = UUID.randomUUID();
        when(scanService.getScanById(scanId, testUserId, Role.ROLE_USER))
                .thenReturn(ScanResponse.builder().id(scanId).build());

        User userWithoutId = User.builder().email("null_id@example.com").role(Role.ROLE_USER).build();
        CustomUserDetails detailsWithNullId = new CustomUserDetails(userWithoutId);

        var response = scanController.getScanById(scanId, detailsWithNullId);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(scanService, times(1)).getScanById(scanId, testUserId, Role.ROLE_USER);
    }

    @Test
    @DisplayName("ScanController helper - authentication không authenticated -> ném InvalidTokenException")
    void testGetScanByIdUnauthenticatedFlag() {
        UUID scanId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken unauth = new UsernamePasswordAuthenticationToken(
                mockUserDetails, null, mockUserDetails.getAuthorities()
        );
        unauth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);

        assertThrows(com.memento3d.identity.domain.exception.InvalidTokenException.class,
                () -> scanController.getScanById(scanId, null));
    }

    @Test
    @DisplayName("ScanController helper - principal là anonymousUser -> ném InvalidTokenException")
    void testGetScanByIdAnonymousUserPrincipal() {
        UUID scanId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken anon = new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, java.util.Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(anon);

        assertThrows(com.memento3d.identity.domain.exception.InvalidTokenException.class,
                () -> scanController.getScanById(scanId, null));
    }

    @Test
    @DisplayName("PATCH /api/v1/scans/{id} - Cập nhật scan thành công trả về 200 + object đã cập nhật")
    void testUpdateScanSuccess() throws Exception {
        UUID scanId = UUID.randomUUID();
        UpdateScanRequest request = UpdateScanRequest.builder()
                .name("Updated Scan Name")
                .description("Updated Description")
                .notes("Updated Notes")
                .build();

        ScanResponse responseDto = ScanResponse.builder()
                .id(scanId)
                .userId(testUserId)
                .name("Updated Scan Name")
                .description("Updated Description")
                .notes("Updated Notes")
                .inputMode("image")
                .status("draft")
                .fileCount(0)
                .totalSizeBytes(0L)
                .createdAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now())
                .build();

        when(scanService.updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(responseDto);

        mockMvc.perform(patch("/api/v1/scans/{id}", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(scanId.toString())))
                .andExpect(jsonPath("$.user_id", is(testUserId.toString())))
                .andExpect(jsonPath("$.name", is("Updated Scan Name")))
                .andExpect(jsonPath("$.description", is("Updated Description")))
                .andExpect(jsonPath("$.notes", is("Updated Notes")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()));

        verify(scanService, times(1)).updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("PATCH /api/v1/scans/{id} - Scan không tồn tại hoặc IDOR -> 404 Not Found")
    void testUpdateScanNotFound() throws Exception {
        UUID scanId = UUID.randomUUID();
        UpdateScanRequest request = UpdateScanRequest.builder().name("New Name").build();

        when(scanService.updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new com.memento3d.shared.exception.ResourceNotFoundException("Scan not found with ID: " + scanId));

        mockMvc.perform(patch("/api/v1/scans/{id}", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Scan not found with ID: " + scanId)));

        verify(scanService, times(1)).updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("PATCH /api/v1/scans/{id} - Scan đang uploading hoặc processing -> 409 Conflict")
    void testUpdateScanConflict() throws Exception {
        UUID scanId = UUID.randomUUID();
        UpdateScanRequest request = UpdateScanRequest.builder().name("New Name").build();

        when(scanService.updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new ScanConflictException("Cannot update scan while it is in processing status"));

        mockMvc.perform(patch("/api/v1/scans/{id}", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", is("Cannot update scan while it is in processing status")));

        verify(scanService, times(1)).updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("PATCH /api/v1/scans/{id} - Tên trống hoặc không hợp lệ -> 400 Bad Request")
    void testUpdateScanBadRequest() throws Exception {
        UUID scanId = UUID.randomUUID();
        UpdateScanRequest request = UpdateScanRequest.builder().name("   ").build();

        when(scanService.updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new IllegalArgumentException("Scan name cannot be empty"));

        mockMvc.perform(patch("/api/v1/scans/{id}", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Scan name cannot be empty")));

        verify(scanService, times(1)).updateScan(eq(scanId), any(UpdateScanRequest.class), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("PATCH /api/v1/scans/{id} - Chưa xác thực -> 401 Unauthorized")
    void testUpdateScanUnauthenticated() throws Exception {
        UUID scanId = UUID.randomUUID();
        UpdateScanRequest request = UpdateScanRequest.builder().name("New Name").build();
        SecurityContextHolder.clearContext();

        mockMvc.perform(patch("/api/v1/scans/{id}", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("No authenticated user in security context")));

        verify(scanService, never()).updateScan(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ScanController helper - updateScan userDetails null fallback to SecurityContext")
    void testUpdateScanFallbackToSecurityContext() {
        UUID scanId = UUID.randomUUID();
        UpdateScanRequest request = UpdateScanRequest.builder().name("Valid").build();
        when(scanService.updateScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(ScanResponse.builder().id(scanId).name("Valid").build());

        var response = scanController.updateScan(scanId, request, null);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(scanService, times(1)).updateScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER));
    }

    // =========================================================================
    // BE1 — GET /api/v1/scans/{id}/images
    // =========================================================================

    @Test
    @DisplayName("BE1-1: GET /{id}/images → 200 + đầy đủ fields (sort_order, upload_status, thumbnail_path)")
    void testGetScanImagesSuccess() throws Exception {
        UUID scanId = UUID.randomUUID();
        UUID img1Id = UUID.randomUUID();
        UUID img2Id = UUID.randomUUID();

        ScanImageResponse img1 = ScanImageResponse.builder()
                .id(img1Id).scanId(scanId).originalFilename("IMG_0001.jpg")
                .thumbnailPath("/thumbs/img_0001.jpg").fileSizeBytes(1048576L)
                .mimeType("image/jpeg").width(4032).height(3024).format("jpg")
                .sortOrder(0).uploadStatus("uploaded").isExcluded(false)
                .needsQualityCheck(false).createdAt(Instant.now()).build();

        ScanImageResponse img2 = ScanImageResponse.builder()
                .id(img2Id).scanId(scanId).originalFilename("IMG_0002.jpg")
                .thumbnailPath("/thumbs/img_0002.jpg").fileSizeBytes(1100000L)
                .mimeType("image/jpeg").width(4032).height(3024).format("jpg")
                .sortOrder(1).uploadStatus("uploaded").isExcluded(false)
                .needsQualityCheck(false).createdAt(Instant.now()).build();

        ScanImageListResponse mockResponse = ScanImageListResponse.builder()
                .images(List.of(img1, img2)).totalCount(2L)
                .page(1).limit(20).totalPages(1).build();

        when(scanService.getScanImages(eq(scanId), eq(testUserId), eq(Role.ROLE_USER), eq(1), eq(20)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/scans/{id}/images", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.total_count", is(2)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.limit", is(20)))
                .andExpect(jsonPath("$.total_pages", is(1)))
                .andExpect(jsonPath("$.images[0].id", is(img1Id.toString())))
                .andExpect(jsonPath("$.images[0].thumbnail_path", is("/thumbs/img_0001.jpg")))
                .andExpect(jsonPath("$.images[0].sort_order", is(0)))
                .andExpect(jsonPath("$.images[0].upload_status", is("uploaded")))
                .andExpect(jsonPath("$.images[0].needs_quality_check", is(false)))
                .andExpect(jsonPath("$.images[1].sort_order", is(1)));

        verify(scanService).getScanImages(eq(scanId), eq(testUserId), eq(Role.ROLE_USER), eq(1), eq(20));
    }

    @Test
    @DisplayName("BE1-2: GET /{id}/images IDOR → 404")
    void testGetScanImagesNotOwner() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.getScanImages(eq(scanId), eq(testUserId), eq(Role.ROLE_USER), eq(1), eq(20)))
                .thenThrow(new com.memento3d.shared.exception.ResourceNotFoundException(
                        "Scan not found with ID: " + scanId));

        mockMvc.perform(get("/api/v1/scans/{id}/images", scanId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));

        verify(scanService).getScanImages(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("BE1-3: GET /{id}/images — chưa xác thực → 401")
    void testGetScanImagesUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/v1/scans/{id}/images", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
        verify(scanService, never()).getScanImages(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("BE1-4: GET /{id}/images — ảnh upload_status=failed vẫn trả về")
    void testGetScanImagesIncludesFailed() throws Exception {
        UUID scanId = UUID.randomUUID();
        UUID failId = UUID.randomUUID();
        ScanImageResponse failed = ScanImageResponse.builder()
                .id(failId).scanId(scanId).originalFilename("IMG_fail.jpg")
                .fileSizeBytes(0L).mimeType("image/jpeg").format("jpg")
                .sortOrder(4).uploadStatus("failed").isExcluded(false)
                .needsQualityCheck(false).createdAt(Instant.now()).build();

        when(scanService.getScanImages(eq(scanId), eq(testUserId), eq(Role.ROLE_USER), eq(1), eq(20)))
                .thenReturn(ScanImageListResponse.builder().images(List.of(failed))
                        .totalCount(1L).page(1).limit(20).totalPages(1).build());

        mockMvc.perform(get("/api/v1/scans/{id}/images", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].upload_status", is("failed")));
    }

    @Test
    @DisplayName("BE1-5: GET /{id}/images — limit > 100 service ném IllegalArgument → 400")
    void testGetScanImagesLimitExceeded() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.getScanImages(eq(scanId), eq(testUserId), eq(Role.ROLE_USER), eq(1), eq(101)))
                .thenThrow(new IllegalArgumentException("Limit must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/scans/{id}/images", scanId).param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Limit must be between 1 and 100")));
    }

    // =========================================================================
    // BE2 — POST /api/v1/scans/{id}/images
    // =========================================================================

    private MockMultipartFile validJpeg(String name) {
        return new MockMultipartFile(
                "file", name, "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, 0x00});
    }

    @Test
    @DisplayName("BE2-1: POST /{id}/images — upload ảnh bù thành công → 201 + record mới + counters cập nhật")
    void testAddImageToScanSuccess() throws Exception {
        UUID scanId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        ScanImageResponse imageResp = ScanImageResponse.builder()
                .id(imageId).scanId(scanId).originalFilename("extra.jpg")
                .fileSizeBytes(204800L).mimeType("image/jpeg").format("jpg")
                .sortOrder(45).uploadStatus("uploaded").isExcluded(false)
                .needsQualityCheck(true).createdAt(Instant.now()).build();

        ScanImageUploadResponse mockResponse = ScanImageUploadResponse.builder()
                .image(imageResp).scanFileCount(46).scanTotalSizeBytes(94371840L).build();

        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(mockResponse);

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId)
                        .file(validJpeg("extra.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image.id", is(imageId.toString())))
                .andExpect(jsonPath("$.image.original_filename", is("extra.jpg")))
                .andExpect(jsonPath("$.image.upload_status", is("uploaded")))
                .andExpect(jsonPath("$.image.needs_quality_check", is(true)))
                .andExpect(jsonPath("$.scan_file_count", is(46)))
                .andExpect(jsonPath("$.scan_total_size_bytes", is(94371840)));

        verify(scanService).addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("BE2-2: POST /{id}/images — vượt quota → 403")
    void testAddImageQuotaExceeded() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new StorageQuotaExceededException("Xoá scan cũ để tiếp tục"));

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId)
                        .file(validJpeg("big.jpg")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", is("Xoá scan cũ để tiếp tục")));
    }

    @Test
    @DisplayName("BE2-3: POST /{id}/images — sai định dạng (gif) → 400, không insert record rác")
    void testAddImageInvalidFormat() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new InvalidFileFormatException(
                        "Unsupported file type 'image/gif'. Allowed: image/jpeg, image/png"));

        MockMultipartFile gif = new MockMultipartFile(
                "file", "anim.gif", "image/gif", new byte[]{0x47, 0x49, 0x46});

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId).file(gif))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is(
                        "Unsupported file type 'image/gif'. Allowed: image/jpeg, image/png")));
    }

    @Test
    @DisplayName("BE2-4: POST /{id}/images — file > 20 MB → 400")
    void testAddImageFileTooLarge() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new FileSizeExceededException(
                        "File 'huge.jpg' exceeds the 20 MB limit"));

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId)
                        .file(validJpeg("huge.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("File 'huge.jpg' exceeds the 20 MB limit")));
    }

    @Test
    @DisplayName("BE2-5: POST /{id}/images — status=processing → 409")
    void testAddImageScanProcessing() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new ScanConflictException(
                        "Cannot add images to a scan with status 'processing'"));

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId)
                        .file(validJpeg("new.jpg")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", is(
                        "Cannot add images to a scan with status 'processing'")));
    }

    @Test
    @DisplayName("BE2-6: POST /{id}/images — status=done → 409")
    void testAddImageScanDone() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new ScanConflictException(
                        "Cannot add images to a scan with status 'done'"));

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId)
                        .file(validJpeg("new.jpg")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("BE2-7: POST /{id}/images IDOR (không sở hữu scan) → 404")
    void testAddImageNotOwner() throws Exception {
        UUID scanId = UUID.randomUUID();
        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new com.memento3d.shared.exception.ResourceNotFoundException(
                        "Scan not found with ID: " + scanId));

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId)
                        .file(validJpeg("new.jpg")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("BE2-8: POST /{id}/images — chưa xác thực → 401")
    void testAddImageUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(multipart("/api/v1/scans/{id}/images", UUID.randomUUID())
                        .file(validJpeg("new.jpg")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
        verify(scanService, never()).addImageToScan(any(), any(), any(), any());
    }

    @Test
    @DisplayName("BE2-9: POST /{id}/images — ảnh mới có needs_quality_check=true khi QC đã chạy trước")
    void testAddImageNeedsQualityCheckFlagSet() throws Exception {
        UUID scanId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        // Simulate: prior QC run exists → needs_quality_check = true
        ScanImageResponse imageResp = ScanImageResponse.builder()
                .id(imageId).scanId(scanId).originalFilename("补拍01.jpg")
                .fileSizeBytes(512000L).mimeType("image/jpeg").format("jpg")
                .sortOrder(100).uploadStatus("uploaded").isExcluded(false)
                .needsQualityCheck(true).createdAt(Instant.now()).build();

        when(scanService.addImageToScan(eq(scanId), any(), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(ScanImageUploadResponse.builder()
                        .image(imageResp).scanFileCount(101).scanTotalSizeBytes(200L).build());

        mockMvc.perform(multipart("/api/v1/scans/{id}/images", scanId)
                        .file(validJpeg("extra.jpg")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image.needs_quality_check", is(true)));
    }

    // =========================================================================
    // GET /api/v1/scans/{id}/quality-runs
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/scans/{id}/quality-runs - Trả về lịch sử run thành công")
    void testGetQualityCheckRunsSuccess() throws Exception {
        UUID scanId = UUID.randomUUID();

        com.memento3d.quality.api.response.QualityCheckRunSummaryResponse run1 = com.memento3d.quality.api.response.QualityCheckRunSummaryResponse.builder()
                .runNumber(2)
                .status("completed")
                .overallScore(new java.math.BigDecimal("85.5"))
                .scoreCategory("green")
                .createdAt(Instant.now())
                .build();

        com.memento3d.quality.api.response.QualityCheckRunSummaryResponse run2 = com.memento3d.quality.api.response.QualityCheckRunSummaryResponse.builder()
                .runNumber(1)
                .status("completed")
                .overallScore(new java.math.BigDecimal("60.0"))
                .scoreCategory("yellow")
                .createdAt(Instant.now())
                .build();

        when(scanService.getQualityCheckRuns(eq(scanId), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(List.of(run1, run2));

        mockMvc.perform(get("/api/v1/scans/{id}/quality-runs", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].run_number", is(2)))
                .andExpect(jsonPath("$[0].status", is("completed")))
                .andExpect(jsonPath("$[0].overall_score", is(85.5)))
                .andExpect(jsonPath("$[0].score_category", is("green")))
                .andExpect(jsonPath("$[0].created_at", notNullValue()))
                .andExpect(jsonPath("$[1].run_number", is(1)));

        verify(scanService, times(1)).getQualityCheckRuns(eq(scanId), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("GET /api/v1/scans/{id}/quality-runs - Scan chưa từng chạy quality check -> trả mảng rỗng")
    void testGetQualityCheckRunsEmpty() throws Exception {
        UUID scanId = UUID.randomUUID();

        when(scanService.getQualityCheckRuns(eq(scanId), eq(testUserId), eq(Role.ROLE_USER)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/scans/{id}/quality-runs", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(scanService, times(1)).getQualityCheckRuns(eq(scanId), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("GET /api/v1/scans/{id}/quality-runs - Scan không tồn tại hoặc IDOR -> 404 Not Found")
    void testGetQualityCheckRunsNotFound() throws Exception {
        UUID scanId = UUID.randomUUID();

        when(scanService.getQualityCheckRuns(eq(scanId), eq(testUserId), eq(Role.ROLE_USER)))
                .thenThrow(new com.memento3d.shared.exception.ResourceNotFoundException("Scan not found with ID: " + scanId));

        mockMvc.perform(get("/api/v1/scans/{id}/quality-runs", scanId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Scan not found with ID: " + scanId)));

        verify(scanService, times(1)).getQualityCheckRuns(eq(scanId), eq(testUserId), eq(Role.ROLE_USER));
    }

    @Test
    @DisplayName("GET /api/v1/scans/{id}/quality-runs - Chưa xác thực -> 401 Unauthorized")
    void testGetQualityCheckRunsUnauthenticated() throws Exception {
        UUID scanId = UUID.randomUUID();
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/scans/{id}/quality-runs", scanId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("No authenticated user in security context")));

    }
}


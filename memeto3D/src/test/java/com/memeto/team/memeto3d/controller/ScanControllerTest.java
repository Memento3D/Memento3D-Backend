package com.memeto.team.memeto3d.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.GlobalExceptionHandler;
import com.memeto.team.memeto3d.exception.StorageQuotaExceededException;
import com.memeto.team.memeto3d.security.CustomUserDetails;
import com.memeto.team.memeto3d.service.ScanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .thenThrow(new com.memeto.team.memeto3d.exception.ResourceNotFoundException("Scan not found with ID: " + scanId));

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

        assertThrows(com.memeto.team.memeto3d.exception.InvalidTokenException.class,
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

        assertThrows(com.memeto.team.memeto3d.exception.InvalidTokenException.class,
                () -> scanController.getScanById(scanId, null));
    }
}

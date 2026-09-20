package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memeto.team.memeto3d.config.JacksonConfig;
import com.memeto.team.memeto3d.config.OpenApiConfig;
import com.memeto.team.memeto3d.entity.BlacklistedToken;
import com.memeto.team.memeto3d.entity.InputMode;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.entity.User;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DtoAndModelTest {

    @Test
    @DisplayName("Test RegisterRequest builder, getters, setters")
    void testRegisterRequest() {
        RegisterRequest req1 = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .fullName("Full Name")
                .build();

        RegisterRequest req2 = new RegisterRequest();
        req2.setEmail("test@example.com");
        req2.setPassword("password123");
        req2.setFullName("Full Name");

        assertEquals(req1.getEmail(), req2.getEmail());
        assertEquals(req1.getPassword(), req2.getPassword());
        assertEquals(req1.getFullName(), req2.getFullName());

        RegisterRequest req3 = new RegisterRequest("test@example.com", "password123", "Full Name");
        assertEquals("test@example.com", req3.getEmail());
    }

    @Test
    @DisplayName("Test LoginRequest builder, getters, setters")
    void testLoginRequest() {
        LoginRequest req1 = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        LoginRequest req2 = new LoginRequest();
        req2.setEmail("test@example.com");
        req2.setPassword("password123");

        assertEquals(req1.getEmail(), req2.getEmail());
        assertEquals(req1.getPassword(), req2.getPassword());

        LoginRequest req3 = new LoginRequest("test@example.com", "password123");
        assertEquals("test@example.com", req3.getEmail());
    }

    @Test
    @DisplayName("Test UserResponse, builder, getters, setters, and fromEntity")
    void testUserResponse() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .email("user@example.com")
                .fullName("User FullName")
                .role(Role.ROLE_ADMIN)
                .build();

        UserResponse response = UserResponse.fromEntity(user);
        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("user@example.com", response.getEmail());
        assertEquals("User FullName", response.getFullName());
        assertEquals(Role.ROLE_ADMIN, response.getRole());

        // Test fromEntity(null)
        assertNull(UserResponse.fromEntity(null));

        UserResponse response2 = new UserResponse(id, "user@example.com", "User FullName", Role.ROLE_ADMIN);
        assertEquals(id, response2.getId());

        UserResponse emptyResponse = new UserResponse();
        emptyResponse.setId(id);
        emptyResponse.setEmail("user@example.com");
        emptyResponse.setFullName("User FullName");
        emptyResponse.setRole(Role.ROLE_ADMIN);
        assertEquals(id, emptyResponse.getId());
        assertEquals("user@example.com", emptyResponse.getEmail());
        assertEquals("User FullName", emptyResponse.getFullName());
        assertEquals(Role.ROLE_ADMIN, emptyResponse.getRole());
    }

    @Test
    @DisplayName("Test RegisterResponse")
    void testRegisterResponse() {
        UserResponse userResponse = UserResponse.builder().email("test@example.com").build();
        RegisterResponse res1 = RegisterResponse.builder()
                .message("Register successfully")
                .user(userResponse)
                .build();

        RegisterResponse res2 = new RegisterResponse();
        res2.setMessage("Register successfully");
        res2.setUser(userResponse);

        assertEquals(res1.getMessage(), res2.getMessage());
        assertEquals(res1.getUser(), res2.getUser());

        RegisterResponse res3 = new RegisterResponse("Register successfully", userResponse);
        assertEquals("Register successfully", res3.getMessage());
    }

    @Test
    @DisplayName("Test AuthResponse")
    void testAuthResponse() {
        UserResponse userResponse = UserResponse.builder().email("test@example.com").build();
        AuthResponse res1 = AuthResponse.builder()
                .accessToken("token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(userResponse)
                .build();

        AuthResponse res2 = new AuthResponse();
        res2.setAccessToken("token");
        res2.setTokenType("Bearer");
        res2.setExpiresIn(3600L);
        res2.setUser(userResponse);

        assertEquals("token", res2.getAccessToken());
        assertEquals("Bearer", res2.getTokenType());
        assertEquals(3600L, res2.getExpiresIn());
        assertEquals(userResponse, res2.getUser());

        AuthResponse res3 = new AuthResponse("token", "Bearer", 3600L, userResponse);
        assertEquals("token", res3.getAccessToken());
    }

    @Test
    @DisplayName("Test LogoutResponse")
    void testLogoutResponse() {
        LogoutResponse res1 = LogoutResponse.builder()
                .message("Logout successfully")
                .details("Stateless")
                .build();

        LogoutResponse res2 = new LogoutResponse();
        res2.setMessage("Logout successfully");
        res2.setDetails("Stateless");

        assertEquals(res1.getMessage(), res2.getMessage());
        assertEquals(res1.getDetails(), res2.getDetails());

        LogoutResponse res3 = new LogoutResponse("Logout successfully", "Stateless");
        assertEquals("Logout successfully", res3.getMessage());
    }

    @Test
    @DisplayName("Test ErrorResponse")
    void testErrorResponse() {
        Instant now = Instant.now();
        Map<String, String> errors = Map.of("field", "error");
        ErrorResponse res1 = ErrorResponse.builder()
                .timestamp(now)
                .status(400)
                .message("Bad Request")
                .path("/api/test")
                .validationErrors(errors)
                .build();

        ErrorResponse res2 = new ErrorResponse();
        res2.setTimestamp(now);
        res2.setStatus(400);
        res2.setMessage("Bad Request");
        res2.setPath("/api/test");
        res2.setValidationErrors(errors);

        assertEquals(400, res2.getStatus());
        assertEquals("Bad Request", res2.getMessage());
        assertEquals("/api/test", res2.getPath());
        assertEquals(errors, res2.getValidationErrors());
        assertEquals(now, res2.getTimestamp());

        ErrorResponse res3 = new ErrorResponse(now, 400, "Bad Request", "/api/test", errors);
        assertEquals(400, res3.getStatus());
    }

    @Test
    @DisplayName("Test User entity")
    void testUserEntity() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        User user = User.builder()
                .id(id)
                .email("user@example.com")
                .password("password")
                .fullName("User Name")
                .role(Role.ROLE_USER)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, user.getId());
        assertEquals("user@example.com", user.getEmail());
        assertEquals("password", user.getPassword());
        assertEquals("User Name", user.getFullName());
        assertEquals(Role.ROLE_USER, user.getRole());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());

        User user2 = new User();
        user2.setId(id);
        user2.setEmail("user@example.com");
        user2.setPassword("password");
        user2.setFullName("User Name");
        user2.setRole(Role.ROLE_USER);
        user2.setCreatedAt(now);
        user2.setUpdatedAt(now);

        assertEquals(user.getId(), user2.getId());

        User userAllArgs = new User(id, "user@example.com", "password", "User Name", Role.ROLE_USER, 0L, 10737418240L, now, now);
        assertEquals(id, userAllArgs.getId());
        assertEquals(0L, userAllArgs.getStorageUsedBytes());
        assertEquals(10737418240L, userAllArgs.getStorageQuotaBytes());
    }

    @Test
    @DisplayName("Test Scan entity, InputMode, ScanStatus")
    void testScanEntityAndEnums() {
        UUID scanId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@example.com").build();
        Instant now = Instant.now();

        Map<String, Object> devInfo = Map.of("model", "iPhone 15");
        Scan scan = Scan.builder()
                .id(scanId)
                .user(user)
                .name("Scan 1")
                .description("Desc")
                .notes("Notes")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .fileCount(0)
                .totalSizeBytes(0L)
                .thumbnailPath("/thumb.jpg")
                .deviceInfo(devInfo)
                .duplicatedFrom(null)
                .createdAt(now)
                .updatedAt(now)
                .deletedAt(null)
                .build();

        assertEquals(scanId, scan.getId());
        assertEquals(user, scan.getUser());
        assertEquals("Scan 1", scan.getName());
        assertEquals("Desc", scan.getDescription());
        assertEquals("Notes", scan.getNotes());
        assertEquals(InputMode.image, scan.getInputMode());
        assertEquals(ScanStatus.draft, scan.getStatus());
        assertEquals(0, scan.getFileCount());
        assertEquals(0L, scan.getTotalSizeBytes());
        assertEquals("/thumb.jpg", scan.getThumbnailPath());
        assertEquals(devInfo, scan.getDeviceInfo());
        assertNull(scan.getDuplicatedFrom());
        assertEquals(now, scan.getCreatedAt());
        assertEquals(now, scan.getUpdatedAt());
        assertNull(scan.getDeletedAt());

        Scan scan2 = new Scan();
        scan2.setId(scanId);
        scan2.setName("Scan 2");
        scan2.setInputMode(InputMode.video);
        scan2.setStatus(ScanStatus.ready);
        scan2.setDeviceInfo(devInfo);
        assertEquals("Scan 2", scan2.getName());
        assertEquals(InputMode.video, scan2.getInputMode());
        assertEquals(ScanStatus.ready, scan2.getStatus());
        assertEquals(devInfo, scan2.getDeviceInfo());

        // Test InputMode methods
        assertTrue(InputMode.isValid("image"));
        assertTrue(InputMode.isValid("video"));
        assertFalse(InputMode.isValid("invalid"));
        assertFalse(InputMode.isValid(null));
        assertEquals(InputMode.image, InputMode.fromString(null));
        assertEquals(InputMode.image, InputMode.fromString(""));
        assertEquals(InputMode.video, InputMode.fromString("video"));
        assertThrows(IllegalArgumentException.class, () -> InputMode.fromString("audio"));

        // Test ScanStatus values
        assertEquals(ScanStatus.draft, ScanStatus.valueOf("draft"));
        assertTrue(ScanStatus.values().length >= 9);
    }

    @Test
    @DisplayName("Test CreateScanRequest and ScanResponse")
    void testScanDtos() {
        CreateScanRequest req = CreateScanRequest.builder()
                .name("Scan A")
                .description("Desc A")
                .notes("Notes A")
                .inputMode("video")
                .build();

        assertEquals("Scan A", req.getName());
        assertEquals("Desc A", req.getDescription());
        assertEquals("Notes A", req.getNotes());
        assertEquals("video", req.getInputMode());

        CreateScanRequest reqDefault = new CreateScanRequest();
        reqDefault.setName("Scan B");
        assertEquals("image", reqDefault.getInputMode());

        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@example.com").build();
        Instant now = Instant.now();
        Map<String, Object> devInfo = Map.of("model", "Pixel 8");

        Scan scan = Scan.builder()
                .id(id)
                .user(user)
                .name("Scan A")
                .description("Desc A")
                .notes("Notes A")
                .inputMode(InputMode.video)
                .status(ScanStatus.draft)
                .fileCount(5)
                .totalSizeBytes(1024L)
                .deviceInfo(devInfo)
                .thumbnailPath("/thumb.jpg")
                .createdAt(now)
                .updatedAt(now)
                .build();

        ScanResponse res = ScanResponse.fromEntity(scan);
        assertNotNull(res);
        assertEquals(id, res.getId());
        assertEquals(userId, res.getUserId());
        assertEquals("Scan A", res.getName());
        assertEquals("Desc A", res.getDescription());
        assertEquals("Notes A", res.getNotes());
        assertEquals("video", res.getInputMode());
        assertEquals("draft", res.getStatus());
        assertEquals(5, res.getFileCount());
        assertEquals(1024L, res.getTotalSizeBytes());
        assertEquals(devInfo, res.getDeviceInfo());
        assertEquals("/thumb.jpg", res.getThumbnailPath());
        assertEquals(now, res.getCreatedAt());
        assertEquals(now, res.getUpdatedAt());

        res.setDeviceInfo(null);
        assertNull(res.getDeviceInfo());

        assertNull(ScanResponse.fromEntity(null));
    }

    @Test
    @DisplayName("Test BlacklistedToken entity")
    void testBlacklistedTokenEntity() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(3600);

        BlacklistedToken token = BlacklistedToken.builder()
                .id(id)
                .token("token-string")
                .expiryDate(expiry)
                .revokedAt(now)
                .build();

        assertEquals(id, token.getId());
        assertEquals("token-string", token.getToken());
        assertEquals(expiry, token.getExpiryDate());
        assertEquals(now, token.getRevokedAt());

        BlacklistedToken token2 = new BlacklistedToken();
        token2.setId(id);
        token2.setToken("token-string");
        token2.setExpiryDate(expiry);
        token2.setRevokedAt(now);

        assertEquals(token.getToken(), token2.getToken());

        BlacklistedToken tokenAllArgs = new BlacklistedToken(id, "token-string", expiry, now);
        assertEquals(id, tokenAllArgs.getId());
    }

    @Test
    @DisplayName("Test Role enum")
    void testRoleEnum() {
        assertEquals(Role.ROLE_USER, Role.valueOf("ROLE_USER"));
        assertEquals(Role.ROLE_ADMIN, Role.valueOf("ROLE_ADMIN"));
        assertEquals(Role.ROLE_OPERATOR, Role.valueOf("ROLE_OPERATOR"));
        assertTrue(Role.values().length >= 3);
    }

    @Test
    @DisplayName("Test JacksonConfig bean creation")
    void testJacksonConfig() {
        JacksonConfig jacksonConfig = new JacksonConfig();
        ObjectMapper mapper = jacksonConfig.objectMapper();
        assertNotNull(mapper);
    }

    @Test
    @DisplayName("Test OpenApiConfig bean creation")
    void testOpenApiConfig() {
        OpenApiConfig openApiConfig = new OpenApiConfig();
        ReflectionTestUtils.setField(openApiConfig, "serverPort", "8080");
        OpenAPI openAPI = openApiConfig.openAPI();
        assertNotNull(openAPI);
        assertEquals("Memento3D REST API", openAPI.getInfo().getTitle());
        assertNotNull(openAPI.getComponents().getSecuritySchemes().get("BearerAuth"));
    }

    @Test
    @DisplayName("Test ScanListResponse builder, getters, setters, items alias, and JSON serialization")
    void testScanListResponse() throws Exception {
        ScanResponse item = ScanResponse.builder()
                .id(UUID.randomUUID())
                .name("Scan A")
                .status("draft")
                .build();

        ScanListResponse res1 = ScanListResponse.builder()
                .scans(List.of(item))
                .totalCount(1L)
                .page(1)
                .limit(20)
                .totalPages(1)
                .build();

        assertEquals(1, res1.getScans().size());
        assertEquals(1, res1.getItems().size());
        assertEquals(1L, res1.getTotalCount());
        assertEquals(1, res1.getPage());
        assertEquals(20, res1.getLimit());
        assertEquals(1, res1.getTotalPages());

        ScanListResponse res2 = new ScanListResponse();
        res2.setScans(List.of(item));
        res2.setTotalCount(5L);
        res2.setPage(2);
        res2.setLimit(10);
        res2.setTotalPages(1);

        assertEquals(5L, res2.getTotalCount());
        assertEquals(2, res2.getPage());
        assertEquals(10, res2.getLimit());
        assertEquals(1, res2.getTotalPages());

        ScanListResponse res3 = new ScanListResponse(List.of(item), 10L, 1, 10, 1);
        assertEquals(10L, res3.getTotalCount());

        // Test setItems when scans is null vs when scans is already set
        ScanListResponse res4 = new ScanListResponse();
        res4.setItems(List.of(item));
        assertEquals(1, res4.getScans().size());
        res4.setItems(List.of()); // shouldn't overwrite if scans is already non-null
        assertEquals(1, res4.getScans().size());

        // JSON serialization verification
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(res1);
        assertTrue(json.contains("\"total_count\":1"));
        assertTrue(json.contains("\"scans\":["));
        assertTrue(json.contains("\"items\":["));
    }

    @Test
    @DisplayName("Test ScanStatus helper methods isValid and fromString")
    void testScanStatusMethods() {
        assertTrue(ScanStatus.isValid("draft"));
        assertTrue(ScanStatus.isValid("DRAFT"));
        assertTrue(ScanStatus.isValid("  ready  "));
        assertTrue(ScanStatus.isValid("done"));
        assertFalse(ScanStatus.isValid(null));
        assertFalse(ScanStatus.isValid(""));
        assertFalse(ScanStatus.isValid("   "));
        assertFalse(ScanStatus.isValid("unknown_status"));

        assertEquals(ScanStatus.draft, ScanStatus.fromString("draft"));
        assertEquals(ScanStatus.ready, ScanStatus.fromString("READY"));
        assertEquals(ScanStatus.uploading, ScanStatus.fromString("  uploading  "));
        assertNull(ScanStatus.fromString(null));
        assertNull(ScanStatus.fromString(""));
        assertNull(ScanStatus.fromString("   "));

        assertThrows(IllegalArgumentException.class, () -> ScanStatus.fromString("invalid_status"));
    }
}

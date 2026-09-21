package com.memeto.team.memeto3d;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memeto.team.memeto3d.dto.AuthResponse;
import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.LoginRequest;
import com.memeto.team.memeto3d.dto.RegisterRequest;
import com.memeto.team.memeto3d.entity.InputMode;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.repository.BlacklistedTokenRepository;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.memeto.team.memeto3d.entity.InputMode;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.memeto.team.memeto3d.dto.UpdateScanRequest;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private JwtService jwtService;

    private String jwtToken;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        scanRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Register a test user
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("scan_user@example.com")
                .password("Password123!")
                .fullName("Scan User")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. Login to get JWT
        LoginRequest loginRequest = LoginRequest.builder()
                .email("scan_user@example.com")
                .password("Password123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );
        jwtToken = authResponse.getAccessToken();
        testUser = userRepository.findByEmail("scan_user@example.com").orElseThrow();
    }

    @Test
    @DisplayName("Integration: POST /api/v1/scans - Tạo thành công với Bearer Token -> 201 Created")
    void testCreateScanIntegrationSuccess() throws Exception {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("Ancient Vase")
                .description("3D scan of an ancient vase")
                .notes("Lighting studio, 60 photos")
                .inputMode("image")
                .build();

        mockMvc.perform(post("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.user_id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.name", is("Ancient Vase")))
                .andExpect(jsonPath("$.description", is("3D scan of an ancient vase")))
                .andExpect(jsonPath("$.notes", is("Lighting studio, 60 photos")))
                .andExpect(jsonPath("$.input_mode", is("image")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.file_count", is(0)))
                .andExpect(jsonPath("$.total_size_bytes", is(0)))
                .andExpect(jsonPath("$.created_at", notNullValue()));
    }

    @Test
    @DisplayName("Integration: POST /api/v1/scans - Tạo scan với payload chuẩn từ User Request")
    void testCreateScanWithUserRequestPayload() throws Exception {
        String jsonPayload = """
                {
                  "name": "My 3D Object Scan",
                  "description": "Scan of a clay sculpture under diffuse lighting",
                  "notes": "Captured 45 photos with turntable",
                  "input_mode": "image"
                }
                """;

        mockMvc.perform(post("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.user_id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.name", is("My 3D Object Scan")))
                .andExpect(jsonPath("$.description", is("Scan of a clay sculpture under diffuse lighting")))
                .andExpect(jsonPath("$.notes", is("Captured 45 photos with turntable")))
                .andExpect(jsonPath("$.input_mode", is("image")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.file_count", is(0)))
                .andExpect(jsonPath("$.total_size_bytes", is(0)))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()));
    }

    @Test
    @DisplayName("Integration: POST /api/v1/scans - Không gửi JWT token -> 401 Unauthorized")
    void testCreateScanUnauthorized() throws Exception {
        CreateScanRequest request = CreateScanRequest.builder()
                .name("Ancient Vase")
                .build();

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Integration: POST /api/v1/scans - name rỗng -> 400 Bad Request")
    void testCreateScanEmptyName() throws Exception {
        String jsonPayload = "{\"name\": \"\", \"input_mode\": \"image\"}";

        mockMvc.perform(post("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.name", is("Name is required")));
    }

    @Test
    @DisplayName("Integration: POST /api/v1/scans - input_mode không hợp lệ -> 400 Bad Request")
    void testCreateScanInvalidInputMode() throws Exception {
        String jsonPayload = "{\"name\": \"Valid Name\", \"input_mode\": \"panoramic\"}";

        mockMvc.perform(post("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.inputMode", is("Input mode must be 'image' or 'video'")));
    }

    @Test
    @DisplayName("Integration: POST /api/v1/scans - Vượt storage quota -> 403 Forbidden ('Xoá scan cũ để tiếp tục')")
    void testCreateScanStorageQuotaExceeded() throws Exception {
        // Set user storage used equal to or higher than quota
        testUser.setStorageUsedBytes(10737418240L);
        testUser.setStorageQuotaBytes(10737418240L);
        userRepository.save(testUser);

        CreateScanRequest request = CreateScanRequest.builder()
                .name("New Scan Over Quota")
                .build();

        mockMvc.perform(post("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", is("Xoá scan cũ để tiếp tục")));
    }

    @Test
    @DisplayName("Integration AC1: GET /api/v1/scans - Không filter -> Trả tất cả scan chưa xoá của user, sort mới nhất")
    void testGetScansWithoutFilterSortsNewest() throws Exception {
        Scan scanOld = scanRepository.saveAndFlush(Scan.builder()
                .user(testUser)
                .name("Old Scan")
                .status(ScanStatus.draft)
                .inputMode(InputMode.image)
                .build());
        Thread.sleep(30);

        Scan scanMid = scanRepository.saveAndFlush(Scan.builder()
                .user(testUser)
                .name("Mid Scan")
                .status(ScanStatus.ready)
                .inputMode(InputMode.image)
                .build());
        Thread.sleep(30);

        Scan scanNew = scanRepository.saveAndFlush(Scan.builder()
                .user(testUser)
                .name("New Scan")
                .status(ScanStatus.done)
                .inputMode(InputMode.video)
                .build());
        Thread.sleep(30);

        Scan scanDeleted = scanRepository.saveAndFlush(Scan.builder()
                .user(testUser)
                .name("Deleted Scan")
                .status(ScanStatus.deleted)
                .inputMode(InputMode.image)
                .deletedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count", is(3)))
                .andExpect(jsonPath("$.scans", hasSize(3)))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.scans[0].name", is("New Scan")))
                .andExpect(jsonPath("$.scans[1].name", is("Mid Scan")))
                .andExpect(jsonPath("$.scans[2].name", is("Old Scan")));

        // Test custom sorting by name asc
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scans[0].name", is("Mid Scan")))
                .andExpect(jsonPath("$.scans[1].name", is("New Scan")))
                .andExpect(jsonPath("$.scans[2].name", is("Old Scan")));
    }

    @Test
    @DisplayName("Integration AC2: GET /api/v1/scans - Filter theo status hoạt động đúng")
    void testGetScansFilterByStatus() throws Exception {
        Scan scanDraft = Scan.builder()
                .user(testUser)
                .name("Draft Scan")
                .status(ScanStatus.draft)
                .inputMode(InputMode.image)
                .build();
        Scan scanReady = Scan.builder()
                .user(testUser)
                .name("Ready Scan")
                .status(ScanStatus.ready)
                .inputMode(InputMode.image)
                .build();
        Scan scanDone = Scan.builder()
                .user(testUser)
                .name("Done Scan")
                .status(ScanStatus.done)
                .inputMode(InputMode.image)
                .build();

        scanRepository.saveAll(List.of(scanDraft, scanReady, scanDone));

        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("status", "ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count", is(1)))
                .andExpect(jsonPath("$.scans", hasSize(1)))
                .andExpect(jsonPath("$.scans[0].name", is("Ready Scan")))
                .andExpect(jsonPath("$.scans[0].status", is("ready")));
    }

    @Test
    @DisplayName("Integration AC3: GET /api/v1/scans - Phân trang trả đúng total_count và không vượt limit")
    void testGetScansPagination() throws Exception {
        List<Scan> scans = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            scans.add(Scan.builder()
                    .user(testUser)
                    .name("Scan #" + String.format("%02d", i))
                    .status(ScanStatus.draft)
                    .inputMode(InputMode.image)
                    .build());
        }
        scanRepository.saveAll(scans);

        // Page 1, limit 10
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count", is(25)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.limit", is(10)))
                .andExpect(jsonPath("$.total_pages", is(3)))
                .andExpect(jsonPath("$.scans", hasSize(10)));

        // Page 3, limit 10 -> remaining 5 scans
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "3")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count", is(25)))
                .andExpect(jsonPath("$.page", is(3)))
                .andExpect(jsonPath("$.scans", hasSize(5)));
    }

    @Test
    @DisplayName("Integration AC4: GET /api/v1/scans - Không trả scan của user khác (test với 2 user)")
    void testGetScansMultiUserIsolation() throws Exception {
        // Create user 2
        RegisterRequest registerUser2 = RegisterRequest.builder()
                .email("other_user@example.com")
                .password("Password123!")
                .fullName("Other User")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerUser2)))
                .andExpect(status().isCreated());

        User otherUser = userRepository.findByEmail("other_user@example.com").orElseThrow();

        // Seed scans: 2 for testUser, 3 for otherUser
        Scan u1Scan1 = Scan.builder().user(testUser).name("User1 Scan A").status(ScanStatus.draft).inputMode(InputMode.image).build();
        Scan u1Scan2 = Scan.builder().user(testUser).name("User1 Scan B").status(ScanStatus.draft).inputMode(InputMode.image).build();
        Scan u2Scan1 = Scan.builder().user(otherUser).name("User2 Scan X").status(ScanStatus.draft).inputMode(InputMode.image).build();
        Scan u2Scan2 = Scan.builder().user(otherUser).name("User2 Scan Y").status(ScanStatus.draft).inputMode(InputMode.image).build();
        Scan u2Scan3 = Scan.builder().user(otherUser).name("User2 Scan Z").status(ScanStatus.draft).inputMode(InputMode.image).build();

        scanRepository.saveAll(List.of(u1Scan1, u1Scan2, u2Scan1, u2Scan2, u2Scan3));

        // Test User 1 requests scans
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count", is(2)))
                .andExpect(jsonPath("$.scans", hasSize(2)))
                .andExpect(jsonPath("$.scans[*].name", containsInAnyOrder("User1 Scan A", "User1 Scan B")))
                .andExpect(jsonPath("$.scans[*].name", not(hasItem("User2 Scan X"))));
    }

    @Test
    @DisplayName("Integration AC5: GET /api/v1/scans - Response time < 200ms với 1000 scans (seed data)")
    void testPerformanceWith1000Scans() throws Exception {
        List<Scan> bulkScans = new ArrayList<>(1000);
        Instant baseTime = Instant.now().minus(1000, ChronoUnit.MINUTES);
        for (int i = 0; i < 1000; i++) {
            bulkScans.add(Scan.builder()
                    .user(testUser)
                    .name("Bulk Scan #" + i)
                    .status(i % 2 == 0 ? ScanStatus.draft : ScanStatus.ready)
                    .inputMode(InputMode.image)
                    .createdAt(baseTime.plus(i, ChronoUnit.MINUTES))
                    .updatedAt(baseTime.plus(i, ChronoUnit.MINUTES))
                    .build());
        }
        scanRepository.saveAll(bulkScans);

        // Warmup call
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk());

        // Measure execution time
        long start = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_count", is(1000)))
                .andExpect(jsonPath("$.scans", hasSize(20)));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 200, "Response time should be < 200ms with 1000 scans, was: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans - Không gửi JWT token -> 401 Unauthorized")
    void testGetScansUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/scans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans - Validation page < 1 -> 400 Bad Request")
    void testGetScansInvalidPageParam() throws Exception {
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans - Validation limit > 100 -> 400 Bad Request")
    void testGetScansInvalidLimitParam() throws Exception {
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans - Status param không hợp lệ -> 400 Bad Request")
    void testGetScansInvalidStatusParam() throws Exception {
        mockMvc.perform(get("/api/v1/scans")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("status", "bad_status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans/{id} - Owner lấy chi tiết scan thành công đầy đủ fields -> 200 OK")
    void testGetScanByIdIntegrationSuccessOwner() throws Exception {
        Map<String, Object> deviceInfo = Map.of("model", "iPhone 15 Pro", "os", "iOS 17.2");
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Ancient Greek Urn")
                .description("Detailed 3D capture of ancient urn")
                .notes("Turntable with lightbox")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .fileCount(50)
                .totalSizeBytes(2048000L)
                .deviceInfo(deviceInfo)
                .thumbnailPath("/thumbnails/urn.jpg")
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        mockMvc.perform(get("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedScan.getId().toString())))
                .andExpect(jsonPath("$.user_id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.name", is("Ancient Greek Urn")))
                .andExpect(jsonPath("$.description", is("Detailed 3D capture of ancient urn")))
                .andExpect(jsonPath("$.notes", is("Turntable with lightbox")))
                .andExpect(jsonPath("$.input_mode", is("image")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.file_count", is(50)))
                .andExpect(jsonPath("$.total_size_bytes", is(2048000)))
                .andExpect(jsonPath("$.thumbnail_path", is("/thumbnails/urn.jpg")))
                .andExpect(jsonPath("$.device_info.model", is("iPhone 15 Pro")))
                .andExpect(jsonPath("$.device_info.os", is("iOS 17.2")))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans/{id} - User A gọi scan của User B -> 404 Not Found (IDOR protection)")
    void testGetScanByIdIdorProtectionUserACallsUserBScan() throws Exception {
        // Create scan owned by testUser (User A)
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Private Scan of User A")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        // Register and login User B
        RegisterRequest registerB = RegisterRequest.builder()
                .email("user_b@example.com")
                .password("Password123!")
                .fullName("User B")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerB)))
                .andExpect(status().isCreated());

        LoginRequest loginB = LoginRequest.builder()
                .email("user_b@example.com")
                .password("Password123!")
                .build();
        MvcResult resultB = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginB)))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse authB = objectMapper.readValue(resultB.getResponse().getContentAsString(), AuthResponse.class);
        String jwtTokenB = authB.getAccessToken();

        // User B calls User A's scan -> must return 404 (not 403)
        mockMvc.perform(get("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Scan not found with ID: " + savedScan.getId())));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans/{id} - Scan đã soft-deleted (deleted_at != null) -> 404 Not Found")
    void testGetScanByIdSoftDeletedReturns404() throws Exception {
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Deleted Scan")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .deletedAt(Instant.now())
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        mockMvc.perform(get("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Scan not found with ID: " + savedScan.getId())));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans/{id} - ID không tồn tại -> 404 Not Found")
    void testGetScanByIdNonExistentReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/scans/{id}", nonExistentId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Scan not found with ID: " + nonExistentId)));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans/{id} - Không gửi JWT token -> 401 Unauthorized")
    void testGetScanByIdUnauthorizedNoToken() throws Exception {
        UUID scanId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/scans/{id}", scanId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans/{id} - Admin xem scan của bất kỳ user nào -> 200 OK")
    void testGetScanByIdAdminCanAccessAnyScan() throws Exception {
        // Create scan owned by testUser
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Scan for Admin review")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        // Create Admin user
        User adminUser = User.builder()
                .email("admin@example.com")
                .password("Password123!")
                .fullName("System Admin")
                .role(Role.ROLE_ADMIN)
                .build();
        userRepository.save(adminUser);
        String adminToken = jwtService.generateToken(adminUser);

        // Admin calls User's scan -> 200 OK
        mockMvc.perform(get("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedScan.getId().toString())))
                .andExpect(jsonPath("$.user_id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.name", is("Scan for Admin review")));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/scans/{id} - Operator xem scan của bất kỳ user nào -> 200 OK")
    void testGetScanByIdOperatorCanAccessAnyScan() throws Exception {
        // Create scan owned by testUser
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Scan for Operator check")
                .inputMode(InputMode.video)
                .status(ScanStatus.ready)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        // Create Operator user
        User operatorUser = User.builder()
                .email("operator@example.com")
                .password("Password123!")
                .fullName("System Operator")
                .role(Role.ROLE_OPERATOR)
                .build();
        userRepository.save(operatorUser);
        String operatorToken = jwtService.generateToken(operatorUser);

        // Operator calls User's scan -> 200 OK
        mockMvc.perform(get("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedScan.getId().toString())))
                .andExpect(jsonPath("$.user_id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.name", is("Scan for Operator check")));
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - Cập nhật name/description/notes thành công")
    void testUpdateScanSuccessIntegration() throws Exception {
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Old Scan Name")
                .description("Old Description")
                .notes("Old Notes")
                .inputMode(InputMode.image)
                .status(ScanStatus.draft)
                .fileCount(0)
                .totalSizeBytes(0L)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        UpdateScanRequest updateRequest = UpdateScanRequest.builder()
                .name("New Scan Name")
                .description("New Description")
                .notes("New Notes")
                .build();

        mockMvc.perform(patch("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedScan.getId().toString())))
                .andExpect(jsonPath("$.user_id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.name", is("New Scan Name")))
                .andExpect(jsonPath("$.description", is("New Description")))
                .andExpect(jsonPath("$.notes", is("New Notes")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.updated_at", notNullValue()));

        Scan dbScan = scanRepository.findById(savedScan.getId()).orElseThrow();
        assertEquals("New Scan Name", dbScan.getName());
        assertEquals("New Description", dbScan.getDescription());
        assertEquals("New Notes", dbScan.getNotes());
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - Gửi field không được phép sửa (status, file_count...) -> bị bỏ qua, không được silent-apply")
    void testUpdateScanDisallowedFieldsIgnoredIntegration() throws Exception {
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Original Scan Name")
                .description("Original Desc")
                .notes("Original Notes")
                .status(ScanStatus.draft)
                .fileCount(5)
                .totalSizeBytes(5000L)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        String maliciousBody = """
                {
                    "name": "Updated Allowed Name",
                    "status": "done",
                    "file_count": 99999,
                    "total_size_bytes": 88888888
                }
                """;

        mockMvc.perform(patch("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Allowed Name")))
                .andExpect(jsonPath("$.status", is("draft")))
                .andExpect(jsonPath("$.file_count", is(5)))
                .andExpect(jsonPath("$.total_size_bytes", is(5000)));

        Scan dbScan = scanRepository.findById(savedScan.getId()).orElseThrow();
        assertEquals("Updated Allowed Name", dbScan.getName());
        assertEquals(ScanStatus.draft, dbScan.getStatus());
        assertEquals(5, dbScan.getFileCount());
        assertEquals(5000L, dbScan.getTotalSizeBytes());
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - Update khi status = 'uploading' -> 409 Conflict")
    void testUpdateScanStatusUploadingConflictIntegration() throws Exception {
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Uploading Scan")
                .status(ScanStatus.uploading)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        UpdateScanRequest req = UpdateScanRequest.builder().name("Try Update").build();

        mockMvc.perform(patch("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("uploading")));
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - Update khi status = 'processing' -> 409 Conflict")
    void testUpdateScanStatusProcessingConflictIntegration() throws Exception {
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Processing Scan")
                .status(ScanStatus.processing)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        UpdateScanRequest req = UpdateScanRequest.builder().name("Try Update").build();

        mockMvc.perform(patch("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("processing")));
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - User A update scan của User B -> 404 Not Found (IDOR)")
    void testUpdateScanOtherUserScanReturns404Integration() throws Exception {
        User userB = User.builder()
                .email("user_b@example.com")
                .password("Password123!")
                .fullName("User B")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(userB);

        Scan scanB = Scan.builder()
                .user(userB)
                .name("User B Private Scan")
                .status(ScanStatus.draft)
                .build();
        Scan savedScanB = scanRepository.saveAndFlush(scanB);

        UpdateScanRequest req = UpdateScanRequest.builder().name("Hacked Name").build();

        // testUser attempts to update user B's scan
        mockMvc.perform(patch("/api/v1/scans/{id}", savedScanB.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Scan not found")));
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - Scan ID không tồn tại -> 404 Not Found")
    void testUpdateScanNonExistentReturns404Integration() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        UpdateScanRequest req = UpdateScanRequest.builder().name("Non-existent").build();

        mockMvc.perform(patch("/api/v1/scans/{id}", nonExistentId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - Không có Token -> 401 Unauthorized")
    void testUpdateScanUnauthenticatedIntegration() throws Exception {
        UUID scanId = UUID.randomUUID();
        UpdateScanRequest req = UpdateScanRequest.builder().name("No Auth").build();

        mockMvc.perform(patch("/api/v1/scans/{id}", scanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Integration: PATCH /api/v1/scans/{id} - Name khoảng trắng rỗng -> 400 Bad Request")
    void testUpdateScanBlankNameBadRequestIntegration() throws Exception {
        Scan scan = Scan.builder()
                .user(testUser)
                .name("Scan Name")
                .status(ScanStatus.draft)
                .build();
        Scan savedScan = scanRepository.saveAndFlush(scan);

        UpdateScanRequest req = UpdateScanRequest.builder().name("   ").build();

        mockMvc.perform(patch("/api/v1/scans/{id}", savedScan.getId())
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", is("Scan name cannot be empty")));
    }
}


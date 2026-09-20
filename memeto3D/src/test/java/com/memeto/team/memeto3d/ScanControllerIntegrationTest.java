package com.memeto.team.memeto3d;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memeto.team.memeto3d.dto.AuthResponse;
import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.LoginRequest;
import com.memeto.team.memeto3d.dto.RegisterRequest;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.repository.BlacklistedTokenRepository;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
}

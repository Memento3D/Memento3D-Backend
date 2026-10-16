package com.memento3d.identity.api;

import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.RefreshTokenRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.api.response.AuthResponse;
import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.infrastructure.persistence.repository.BlacklistedTokenRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.memento3d.identity.infrastructure.persistence.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired(required = false)
    private com.memento3d.scans.infrastructure.persistence.repository.ScanRepository scanRepository;

    @BeforeEach
    void cleanDatabase() {
        blacklistedTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        if (scanRepository != null) {
            scanRepository.deleteAll();
        }
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Kịch bản 1: Register thành công")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("user1@gmail.com")
                .password("123456")
                .fullName("Nguyen Van A")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Register successfully")))
                .andExpect(jsonPath("$.user.id", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("user1@gmail.com")))
                .andExpect(jsonPath("$.user.fullName", is("Nguyen Van A")));
    }

    @Test
    @DisplayName("Kịch bản 2: Register email đã tồn tại trả về 409 Conflict")
    void testRegisterDuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("user_dup@gmail.com")
                .password("123456")
                .fullName("Nguyen Van A")
                .build();

        // Lần 1: Thành công
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Lần 2: Trùng email -> 409 Conflict
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("Kịch bản 3: Login thành công trả về JWT")
    void testLoginSuccess() throws Exception {
        // Đăng ký user trước
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("login_user@gmail.com")
                .password("123456")
                .fullName("Nguyen Van B")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Đăng nhập
        LoginRequest loginRequest = LoginRequest.builder()
                .email("login_user@gmail.com")
                .password("123456")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(3600)))
                .andExpect(jsonPath("$.user.email", is("login_user@gmail.com")));
    }

    @Test
    @DisplayName("Kịch bản 4: Login sai password trả về 401 Unauthorized")
    void testLoginWrongPassword() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("wrong_pass_user@gmail.com")
                .password("123456")
                .fullName("Nguyen Van C")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("wrong_pass_user@gmail.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid credentials")));
    }

    @Test
    @DisplayName("Kịch bản 5: Request không có JWT vào /api/auth/me trả về 401 Unauthorized")
    void testGetMeWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Kịch bản 6 & 7: Get current user với JWT hợp lệ và Logout blacklist token")
    void testGetMeAndLogoutFlow() throws Exception {
        // 1. Register
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("flow_user@gmail.com")
                .password("123456")
                .fullName("Nguyen Van D")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. Login để lấy JWT
        LoginRequest loginRequest = LoginRequest.builder()
                .email("flow_user@gmail.com")
                .password("123456")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String token = authResponse.getAccessToken();
        String authHeader = "Bearer " + token;

        // 3. Gọi GET /api/auth/me với valid token -> 200 OK
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("flow_user@gmail.com")))
                .andExpect(jsonPath("$.fullName", is("Nguyen Van D")));

        // 4. Gọi POST /api/auth/logout -> 200 OK
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Logout successfully")));

        // 5. Thử gọi lại GET /api/auth/me với token đã bị logout/blacklist -> 401 Unauthorized
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", authHeader))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Kịch bản 8: Register validation error - Payload không hợp lệ trả về 400")
    void testRegisterValidationError() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("not-an-email")
                .password("123")
                .fullName("")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.email", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.password", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.fullName", notNullValue()));
    }

    @Test
    @DisplayName("Kịch bản 9: Login validation error - Payload thiếu email/password trả về 400")
    void testLoginValidationError() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("")
                .password("")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.email", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.password", notNullValue()));
    }

    @Test
    @DisplayName("Kịch bản 10: Logout không có header Authorization bị chặn bởi Spring Security 401")
    void testLogoutWithoutHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Kịch bản 11: GET /api/auth/me với token sai định dạng trả về 401 Unauthorized")
    void testGetMeWithMalformedToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }
}


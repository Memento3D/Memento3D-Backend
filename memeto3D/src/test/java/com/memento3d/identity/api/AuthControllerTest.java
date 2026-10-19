package com.memento3d.identity.api;

import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.api.response.AuthResponse;
import com.memento3d.identity.api.response.LogoutResponse;
import com.memento3d.identity.api.response.RegisterResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.exception.EmailAlreadyExistsException;
import com.memento3d.shared.api.GlobalExceptionHandler;
import com.memento3d.identity.application.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/auth/register - Thành công trả về 201")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("123456")
                .fullName("Test User")
                .agreeTerms(true)
                .termsVersion("1.0")
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        RegisterResponse response = RegisterResponse.builder()
                .message("Register successfully")
                .user(userResponse)
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Register successfully")))
                .andExpect(jsonPath("$.user.email", is("test@example.com")))
                .andExpect(jsonPath("$.user.fullName", is("Test User")));

        verify(authService, times(1)).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - Dữ liệu không hợp lệ trả về 400 Bad Request")
    void testRegisterInvalidPayload() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .email("invalid-email")
                .password("")
                .fullName("")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors").exists());

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("POST /api/auth/register - Email đã tồn tại trả về 409 Conflict")
    void testRegisterDuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("123456")
                .fullName("Duplicate User")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Email is already registered: duplicate@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", is("Email is already registered: duplicate@example.com")));
    }

    @Test
    @DisplayName("POST /api/auth/login - Thành công trả về 200 OK")
    void testLoginSuccess() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("123456")
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("jwt.token.here")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(userResponse)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("jwt.token.here")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(3600)))
                .andExpect(jsonPath("$.user.email", is("test@example.com")));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login - Dữ liệu không hợp lệ trả về 400 Bad Request")
    void testLoginInvalidPayload() throws Exception {
        LoginRequest invalidRequest = LoginRequest.builder()
                .email("")
                .password("")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("POST /api/auth/login - Sai mật khẩu trả về 401 Unauthorized")
    void testLoginBadCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongpassword")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid credentials")));
    }


    @Test
    @DisplayName("POST /api/auth/logout - Với Authorization header trả về 200 OK")
    void testLogoutWithHeader() throws Exception {
        LogoutResponse response = LogoutResponse.builder()
                .message("Logout successfully")
                .details("Token invalidated")
                .build();

        when(authService.logout(eq("Bearer valid-token"), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Logout successfully")));

        verify(authService, times(1)).logout(eq("Bearer valid-token"), any());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Không có Authorization header vẫn trả về 200 OK")
    void testLogoutWithoutHeader() throws Exception {
        LogoutResponse response = LogoutResponse.builder()
                .message("Logout successfully")
                .details("Token invalidated")
                .build();

        when(authService.logout(isNull(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Logout successfully")));

        verify(authService, times(1)).logout(isNull(), any());
    }

    @Test
    @DisplayName("POST /api/auth/email/verify - Thành công trả về 200 OK")
    void testVerifyEmailSuccess() throws Exception {
        com.memento3d.identity.api.request.VerifyEmailRequest request =
                new com.memento3d.identity.api.request.VerifyEmailRequest("valid-token-123");

        com.memento3d.identity.api.response.VerifyEmailResponse response =
                com.memento3d.identity.api.response.VerifyEmailResponse.builder()
                        .message("Email verified successfully")
                        .email("test@example.com")
                        .verified(true)
                        .build();

        when(authService.verifyEmail(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified", is(true)))
                .andExpect(jsonPath("$.email", is("test@example.com")));

        verify(authService, times(1)).verifyEmail(any());
    }

    @Test
    @DisplayName("POST /api/auth/email/resend-verification - Thành công trả về 200 OK")
    void testResendVerificationSuccess() throws Exception {
        com.memento3d.identity.api.request.ResendVerificationRequest request =
                new com.memento3d.identity.api.request.ResendVerificationRequest("test@example.com");

        doNothing().when(authService).resendVerificationEmail(any());

        mockMvc.perform(post("/api/auth/email/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(authService, times(1)).resendVerificationEmail(any());
    }

    @Test
    @DisplayName("POST /api/auth/consents - Thành công trả về 200 OK với AuthResponse")
    void testCompleteConsentsSuccess() throws Exception {
        com.memento3d.identity.api.request.ConsentRequest request =
                com.memento3d.identity.api.request.ConsentRequest.builder()
                        .agreeTerms(true)
                        .termsVersion("1.0")
                        .marketingConsent(false)
                        .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("new-jwt-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();

        when(authService.completeConsents(any(), any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("new-jwt-token")))
                .andExpect(jsonPath("$.refreshToken", is("new-refresh-token")));

        verify(authService, times(1)).completeConsents(any(), any(), any());
    }
}

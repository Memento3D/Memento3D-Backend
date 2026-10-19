package com.memento3d.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.api.request.ConsentRequest;
import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RefreshTokenRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.api.request.ResendVerificationRequest;
import com.memento3d.identity.api.request.VerifyEmailRequest;
import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.EmailVerificationToken;
import com.memento3d.identity.domain.model.LegalDocument;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.infrastructure.persistence.repository.BlacklistedTokenRepository;
import com.memento3d.identity.infrastructure.persistence.repository.EmailVerificationTokenRepository;
import com.memento3d.identity.infrastructure.persistence.repository.LegalDocumentRepository;
import com.memento3d.identity.infrastructure.persistence.repository.RefreshTokenRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserConsentRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserIdentityRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.infrastructure.security.JwtService;
import com.memento3d.identity.infrastructure.security.oauth2.CustomOAuth2UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
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
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private LegalDocumentRepository legalDocumentRepository;

    @Autowired
    private UserConsentRepository userConsentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired(required = false)
    private com.memento3d.assets.infrastructure.persistence.repository.UploadChunkRepository uploadChunkRepository;

    @Autowired(required = false)
    private com.memento3d.scans.infrastructure.persistence.repository.ScanRepository scanRepository;

    @Autowired(required = false)
    private com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseMembershipRepository warehouseMembershipRepository;

    @BeforeEach
    void cleanDatabase() {
        blacklistedTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userConsentRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        userIdentityRepository.deleteAll();
        if (uploadChunkRepository != null) {
            uploadChunkRepository.deleteAll();
        }
        if (scanRepository != null) {
            scanRepository.deleteAll();
        }
        if (warehouseMembershipRepository != null) {
            warehouseMembershipRepository.deleteAll();
        }
        userRepository.deleteAll();
        legalDocumentRepository.deleteAll();
        legalDocumentRepository.flush();

        if (legalDocumentRepository.findByTypeAndVersion("TERMS", "1.0").isEmpty()) {
            LegalDocument terms = LegalDocument.builder()
                    .type("TERMS")
                    .version("1.0")
                    .title("MEMENTO3D — ĐIỀU KHOẢN SỬ DỤNG VÀ THỎA THUẬN TÀI KHOẢN")
                    .content("# Điều khoản sử dụng Memento3D")
                    .effectiveAt(Instant.now())
                    .active(true)
                    .build();
            legalDocumentRepository.saveAndFlush(terms);
        }

        if (legalDocumentRepository.findByTypeAndVersion("PRIVACY", "1.0").isEmpty()) {
            LegalDocument privacy = LegalDocument.builder()
                    .type("PRIVACY")
                    .version("1.0")
                    .title("MEMENTO3D — CHÍNH SÁCH QUYỀN RIÊNG TƯ VÀ BẢO VỆ DỮ LIỆU CÁ NHÂN")
                    .content("# Chính sách quyền riêng tư Memento3D")
                    .effectiveAt(Instant.now())
                    .active(true)
                    .build();
            legalDocumentRepository.saveAndFlush(privacy);
        }
    }

    @Test
    @DisplayName("Kịch bản 1: Local register thành công, gán role CUSTOMER, tạo unverified account và lưu consents")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("user1@gmail.com")
                .password("SecurePass123!")
                .fullName("Nguyen Van A")
                .agreeTerms(true)
                .termsVersion("1.0")
                .marketingConsent(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.user.id", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("user1@gmail.com")))
                .andExpect(jsonPath("$.user.fullName", is("Nguyen Van A")))
                .andExpect(jsonPath("$.user.role", is("ROLE_USER")))
                .andExpect(jsonPath("$.user.emailVerified", is(false)))
                .andExpect(jsonPath("$.verificationRequired", is(true)))
                .andExpect(jsonPath("$.verificationToken", notNullValue()))
                .andReturn();

        // Assert database state
        User user = userRepository.findByEmail("user1@gmail.com").orElseThrow();
        assertEquals(Role.ROLE_USER, user.getRole());
        assertFalse(user.getEmailVerified());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());

        // Assert consent recorded
        assertTrue(userConsentRepository.existsByUserIdAndConsentTypeAndDocumentVersionAndAcceptedTrue(
                user.getId(), "TERMS", "1.0"));
    }

    @Test
    @DisplayName("Kịch bản 2: Local register thiếu Terms -> Bị từ chối với 400 Bad Request")
    void testRegisterMissingTerms() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("noterms@gmail.com")
                .password("SecurePass123!")
                .fullName("No Terms User")
                .agreeTerms(false) // Missing required agreement
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("TERMS_AGREEMENT_REQUIRED")));
    }

    @Test
    @DisplayName("Kịch bản 3: Email verification thành công kích hoạt tài khoản")
    void testEmailVerificationSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("verify_me@gmail.com")
                .password("SecurePass123!")
                .fullName("Verify Me")
                .agreeTerms(true)
                .termsVersion("1.0")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String token = jsonNode.get("verificationToken").asText();

        // Verify email
        VerifyEmailRequest verifyRequest = new VerifyEmailRequest(token);
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified", is(true)))
                .andExpect(jsonPath("$.email", is("verify_me@gmail.com")));

        // Account is now ACTIVE and verified
        User user = userRepository.findByEmail("verify_me@gmail.com").orElseThrow();
        assertTrue(user.getEmailVerified());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("Kịch bản 4: Email verification token expired hoặc reused -> Bị từ chối với 400 Bad Request")
    void testEmailVerificationTokenExpiredOrReused() throws Exception {
        User user = User.builder()
                .email("expired_token@gmail.com")
                .password(passwordEncoder.encode("Password123!"))
                .fullName("Expired User")
                .role(Role.ROLE_USER)
                .emailVerified(false)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();
        User savedUser = userRepository.save(user);

        // Expired token
        EmailVerificationToken expiredToken = EmailVerificationToken.builder()
                .user(savedUser)
                .token("expired-token-uuid")
                .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();
        emailVerificationTokenRepository.save(expiredToken);

        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("expired-token-uuid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        // Reused token
        EmailVerificationToken usedToken = EmailVerificationToken.builder()
                .user(savedUser)
                .token("used-token-uuid")
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .usedAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();
        emailVerificationTokenRepository.save(usedToken);

        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("used-token-uuid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Kịch bản 5: Register email đã tồn tại trả về 409 Conflict")
    void testRegisterDuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("user_dup@gmail.com")
                .password("SecurePass123!")
                .fullName("Nguyen Van A")
                .agreeTerms(true)
                .termsVersion("1.0")
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
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Kịch bản 6: Email chưa xác thực không được phép đăng nhập (403 Forbidden)")
    void testLoginUnverifiedEmailBlocked() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("unverified@gmail.com")
                .password("SecurePass123!")
                .fullName("Unverified User")
                .agreeTerms(true)
                .termsVersion("1.0")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Đăng nhập khi chưa xác thực -> 403 Forbidden with EMAIL_NOT_VERIFIED
        LoginRequest loginRequest = LoginRequest.builder()
                .email("unverified@gmail.com")
                .password("SecurePass123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("EMAIL_NOT_VERIFIED")));
    }

    @Test
    @DisplayName("Kịch bản 7: Login thành công sau khi xác thực email")
    void testLoginSuccessAfterVerification() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("login_user@gmail.com")
                .password("SecurePass123!")
                .fullName("Nguyen Van B")
                .agreeTerms(true)
                .termsVersion("1.0")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .get("verificationToken").asText();

        // Kích hoạt email
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(token))))
                .andExpect(status().isOk());

        // Đăng nhập thành công
        LoginRequest loginRequest = LoginRequest.builder()
                .email("login_user@gmail.com")
                .password("SecurePass123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.user.email", is("login_user@gmail.com")))
                .andExpect(jsonPath("$.user.role", is("ROLE_USER")));
    }

    @Test
    @DisplayName("Kịch bản 8: Resend verification email có cơ chế rate limit chống spam")
    void testResendVerificationEmailRateLimit() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("spam_test@gmail.com")
                .password("SecurePass123!")
                .fullName("Spam Test")
                .agreeTerms(true)
                .termsVersion("1.0")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        ResendVerificationRequest resendRequest = new ResendVerificationRequest("spam_test@gmail.com");

        // Gọi ngay lập tức (< 60s) -> 429 Too Many Requests
        mockMvc.perform(post("/api/auth/email/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error", is("RATE_LIMIT_EXCEEDED")));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Kịch bản 9: Google register lần đầu & Google login lần thứ hai không tạo user trùng")
    void testGoogleOAuth2FirstAndSecondLogin() {
        OAuth2User googleOAuth2User = new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-sub-12345",
                        "email", "google.customer@gmail.com",
                        "name", "Google Customer",
                        "picture", "https://lh3.googleusercontent.com/avatar.jpg",
                        "email_verified", true
                ),
                "sub"
        );

        // Lần 1: Google register lần đầu
        User firstLoginUser = customOAuth2UserService.processUserFromOAuth2(googleOAuth2User, "google");
        assertNotNull(firstLoginUser);
        assertEquals("google.customer@gmail.com", firstLoginUser.getEmail());
        assertEquals(Role.ROLE_USER, firstLoginUser.getRole());
        assertEquals("google-sub-12345", firstLoginUser.getProviderId());
        assertTrue(firstLoginUser.getEmailVerified());

        // Lần 2: Google login lần thứ hai
        User secondLoginUser = customOAuth2UserService.processUserFromOAuth2(googleOAuth2User, "google");
        assertEquals(firstLoginUser.getId(), secondLoginUser.getId());

        // Verify counts
        assertEquals(1, userRepository.count());
        assertEquals(1, userIdentityRepository.count());
    }

    @Test
    @DisplayName("Kịch bản 10: Google trùng email với local account -> Từ chối tự động liên kết (account_exists_link_required)")
    void testGoogleConflictWithExistingLocalAccount() {
        // Tạo tài khoản LOCAL trước
        User localUser = User.builder()
                .email("conflict@gmail.com")
                .password(passwordEncoder.encode("LocalPassword123!"))
                .fullName("Local User")
                .role(Role.ROLE_USER)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(localUser);

        // Google gửi cùng email nhưng chưa được liên kết
        OAuth2User googleOAuth2User = new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-sub-conflict-999",
                        "email", "conflict@gmail.com",
                        "name", "Google Conflict User",
                        "email_verified", true
                ),
                "sub"
        );

        // Phải ném ngoại lệ OAuth2AuthenticationException
        OAuth2AuthenticationException exception = assertThrows(
                OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.processUserFromOAuth2(googleOAuth2User, "google")
        );

        assertEquals("account_exists_link_required", exception.getError().getErrorCode());
    }

    @Test
    @DisplayName("Kịch bản 11: GET /api/v1/legal/terms/current và /privacy/current trả về văn bản hợp lệ")
    void testLegalEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/legal/terms/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("TERMS")))
                .andExpect(jsonPath("$.version", is("1.0")))
                .andExpect(jsonPath("$.content", notNullValue()));

        mockMvc.perform(get("/api/v1/legal/privacy/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("PRIVACY")))
                .andExpect(jsonPath("$.version", is("1.0")))
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    @Test
    @DisplayName("Kịch bản 12: Hoàn tất đồng ý Terms qua POST /api/auth/consents")
    void testCompleteConsents() throws Exception {
        User user = User.builder()
                .email("consent_user@gmail.com")
                .fullName("Consent User")
                .role(Role.ROLE_USER)
                .provider(AuthProvider.GOOGLE)
                .providerId("google-sub-consent-1")
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        ConsentRequest request = ConsentRequest.builder()
                .agreeTerms(true)
                .termsVersion("1.0")
                .marketingConsent(true)
                .build();

        mockMvc.perform(post("/api/auth/consents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));

        assertTrue(userConsentRepository.existsByUserIdAndConsentTypeAndDocumentVersionAndAcceptedTrue(
                user.getId(), "TERMS", "1.0"));
    }
}

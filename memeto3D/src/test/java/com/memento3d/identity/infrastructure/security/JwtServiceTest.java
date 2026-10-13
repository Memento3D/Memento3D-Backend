package com.memento3d.identity.infrastructure.security;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;
    private CustomUserDetails userDetails;

    // 256-bit test secret
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", expirationMs);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encoded_secret")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();

        userDetails = new CustomUserDetails(testUser);
    }

    @Test
    @DisplayName("JWT hợp lệ - Generate and validate valid token")
    void testValidJwtToken() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token);
        assertFalse(token.isBlank());

        String username = jwtService.extractUsername(token);
        assertEquals(testUser.getEmail(), username);

        UUID extractedUserId = jwtService.extractUserId(token);
        assertEquals(testUser.getId(), extractedUserId);

        assertTrue(jwtService.isTokenValid(token, userDetails));
        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    @DisplayName("JWT hết hạn - Expired token is detected")
    void testExpiredJwtToken() {
        // Generate token with negative expiration time (already expired)
        String expiredToken = jwtService.generateToken(Collections.emptyMap(), testUser.getEmail(), -1000);

        assertNotNull(expiredToken);
        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(expiredToken));
    }

    @Test
    @DisplayName("JWT không hợp lệ - Malformed or invalid token signature")
    void testInvalidJwtToken() {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalidpayload.invalidsignature";

        assertThrows(JwtException.class, () -> jwtService.extractUsername(invalidToken));
    }

    @Test
    @DisplayName("Test expiration getters and token extraction")
    void testExpirationGettersAndClaims() {
        assertEquals(3600L, jwtService.getExpirationInSeconds());
        assertEquals(3600000L, jwtService.getJwtExpirationMs());

        String token = jwtService.generateToken(testUser);
        assertNotNull(jwtService.extractExpiration(token));

        // Token without userId claim
        String tokenWithoutUserId = jwtService.generateToken(Collections.emptyMap(), "simple@example.com", 60000);
        assertNull(jwtService.extractUserId(tokenWithoutUserId));
    }

    @Test
    @DisplayName("Test non-base64 raw secret and short secret fallback to SHA-256")
    void testSigningKeyFallbacks() {
        JwtService rawSecretJwtService = new JwtService();
        ReflectionTestUtils.setField(rawSecretJwtService, "jwtSecret", "non-base64-plain-text-secret-key-that-is-not-valid-base64!@#$%");
        ReflectionTestUtils.setField(rawSecretJwtService, "jwtExpirationMs", 3600000L);

        String token = rawSecretJwtService.generateToken(testUser);
        assertNotNull(token);
        assertEquals(testUser.getEmail(), rawSecretJwtService.extractUsername(token));

        JwtService shortSecretJwtService = new JwtService();
        // Base64 of a short string (< 32 bytes)
        ReflectionTestUtils.setField(shortSecretJwtService, "jwtSecret", "c2hvcnQ=");
        ReflectionTestUtils.setField(shortSecretJwtService, "jwtExpirationMs", 3600000L);

        String shortKeyToken = shortSecretJwtService.generateToken(testUser);
        assertNotNull(shortKeyToken);
        assertEquals(testUser.getEmail(), shortSecretJwtService.extractUsername(shortKeyToken));
    }

    @Test
    @DisplayName("isTokenValid returns false when username does not match UserDetails")
    void testTokenInvalidWhenUsernameMismatch() {
        String token = jwtService.generateToken(testUser);

        User anotherUser = User.builder()
                .id(UUID.randomUUID())
                .email("different@example.com")
                .password("pass")
                .fullName("Different User")
                .role(Role.ROLE_USER)
                .build();
        CustomUserDetails differentUserDetails = new CustomUserDetails(anotherUser);

        assertFalse(jwtService.isTokenValid(token, differentUserDetails));
    }
}



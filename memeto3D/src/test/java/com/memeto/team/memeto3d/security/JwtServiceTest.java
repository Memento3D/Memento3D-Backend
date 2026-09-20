package com.memeto.team.memeto3d.security;

import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.User;
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
}

package com.memento3d.shared.security;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.identity.infrastructure.security.CustomUserDetailsService;
import com.memento3d.identity.infrastructure.security.JwtService;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.application.service.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private User sampleUser;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("password")
                .fullName("User Name")
                .role(Role.ROLE_USER)
                .build();
        userDetails = new CustomUserDetails(sampleUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Filter bypasses when Authorization header is missing")
    void testMissingAuthHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tokenBlacklistService, jwtService, userDetailsService);
    }

    @Test
    @DisplayName("Filter bypasses when Authorization header does not start with Bearer")
    void testNonBearerAuthHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tokenBlacklistService, jwtService, userDetailsService);
    }

    @Test
    @DisplayName("Filter blocks blacklisted token")
    void testBlacklistedToken() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer blacklisted.token.here");
        when(tokenBlacklistService.isBlacklisted("blacklisted.token.here")).thenReturn(true);
        when(request.getRequestURI()).thenReturn("/api/auth/me");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("Filter authenticates user with valid JWT")
    void testValidTokenAuthenticatesUser() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user@example.com", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    @DisplayName("Filter skips authentication when user is already authenticated in context")
    void testAlreadyAuthenticated() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenReturn("user@example.com");

        UsernamePasswordAuthenticationToken existingAuth = new UsernamePasswordAuthenticationToken("existing", null, null);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(existingAuth, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Filter does not authenticate when username extracted is null")
    void testNullUsernameExtracted() throws ServletException, IOException {
        String token = "null.user.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Filter does not authenticate when token is invalid according to isTokenValid")
    void testTokenValidationFails() throws ServletException, IOException {
        String token = "invalid.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Filter catches JwtException gracefully and proceeds chain")
    void testJwtExceptionCaught() throws ServletException, IOException {
        String token = "corrupted.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenThrow(new JwtException("Signature invalid"));
        when(request.getRequestURI()).thenReturn("/api/auth/me");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Filter catches IllegalArgumentException gracefully and proceeds chain")
    void testIllegalArgumentExceptionCaught() throws ServletException, IOException {
        String token = "empty.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenThrow(new IllegalArgumentException("JWT claims string is empty"));
        when(request.getRequestURI()).thenReturn("/api/auth/me");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

package com.memento3d.shared.security;

import com.memento3d.identity.infrastructure.security.CustomUserDetailsService;
import com.memento3d.shared.security.JwtAuthenticationEntryPoint;
import com.memento3d.shared.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private com.memento3d.identity.infrastructure.security.oauth2.CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private com.memento3d.identity.infrastructure.security.oauth2.OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @Mock
    private com.memento3d.identity.infrastructure.security.oauth2.OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;

    @Mock
    private com.memento3d.identity.infrastructure.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(
                jwtAuthenticationFilter,
                jwtAuthenticationEntryPoint,
                userDetailsService,
                customOAuth2UserService,
                oauth2AuthenticationSuccessHandler,
                oauth2AuthenticationFailureHandler,
                httpCookieOAuth2AuthorizationRequestRepository
        );
        ReflectionTestUtils.setField(securityConfig, "allowedOrigins", "http://localhost:3000,http://localhost:8080");
    }

    @Test
    @DisplayName("passwordEncoder bean returns BCryptPasswordEncoder")
    void testPasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertNotNull(encoder);
        String raw = "password123";
        String encoded = encoder.encode(raw);
        assertTrue(encoder.matches(raw, encoded));
    }

    @Test
    @DisplayName("corsConfigurationSource bean configures CORS correctly")
    void testCorsConfigurationSource() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");
        CorsConfiguration corsConfig = source.getCorsConfiguration(request);

        assertNotNull(corsConfig);
        assertTrue(corsConfig.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(corsConfig.getAllowCredentials());
    }

    @Test
    @DisplayName("authenticationProvider bean configures DaoAuthenticationProvider")
    void testAuthenticationProvider() {
        AuthenticationProvider provider = securityConfig.authenticationProvider();
        assertNotNull(provider);
    }

    @Test
    @DisplayName("authenticationManager delegates to AuthenticationConfiguration")
    void testAuthenticationManager() throws Exception {
        AuthenticationConfiguration config = mock(AuthenticationConfiguration.class);
        AuthenticationManager expectedManager = mock(AuthenticationManager.class);
        when(config.getAuthenticationManager()).thenReturn(expectedManager);

        AuthenticationManager actualManager = securityConfig.authenticationManager(config);
        assertEquals(expectedManager, actualManager);
    }
}

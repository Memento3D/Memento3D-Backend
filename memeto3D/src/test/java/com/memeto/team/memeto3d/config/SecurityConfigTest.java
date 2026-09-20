package com.memeto.team.memeto3d.config;

import com.memeto.team.memeto3d.security.CustomUserDetailsService;
import com.memeto.team.memeto3d.security.JwtAuthenticationEntryPoint;
import com.memeto.team.memeto3d.security.JwtAuthenticationFilter;
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

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(jwtAuthenticationFilter, jwtAuthenticationEntryPoint, userDetailsService);
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

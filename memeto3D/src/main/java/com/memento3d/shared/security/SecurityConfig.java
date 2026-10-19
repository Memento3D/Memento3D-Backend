package com.memento3d.shared.security;

import com.memento3d.identity.infrastructure.security.CustomUserDetailsService;
import com.memento3d.identity.infrastructure.security.oauth2.CustomOAuth2UserService;
import com.memento3d.identity.infrastructure.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import com.memento3d.identity.infrastructure.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.memento3d.identity.infrastructure.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.memento3d.shared.security.JwtAuthenticationEntryPoint;
import com.memento3d.shared.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final com.memento3d.identity.infrastructure.security.oauth2.CustomOidcUserService customOidcUserService;
    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:5176}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/auth/register", "/auth/register",
                                "/api/v1/auth/login", "/api/auth/login", "/auth/login",
                                "/api/v1/auth/refresh", "/api/auth/refresh", "/auth/refresh",
                                "/api/v1/auth/forgot-password", "/api/auth/forgot-password", "/auth/forgot-password",
                                "/api/v1/auth/reset-password", "/api/auth/reset-password", "/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/api/v1/auth/oauth2/**",
                                "/api/auth/oauth2/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/email/**", "/api/auth/email/**", "/auth/email/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/legal/**", "/api/legal/**", "/legal/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/webhooks/payments/**",
                                "/api/payments/webhooks/**",
                                "/payments/webhooks/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/scans/*/ratings", "/api/v1/scans/*/rating").permitAll()
                        // Local Printer Agent gateway (authenticated by X-Agent-Token inside the service)
                        .requestMatchers(HttpMethod.POST, "/api/v1/agent/heartbeat").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/scans/*/ratings", "/api/v1/scans/*/rating").permitAll()
                        // Public Storefront Catalog
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products", "/api/v1/products/**",
                                "/api/products", "/api/products/**",
                                "/api/v1/product-categories", "/api/v1/product-categories/**",
                                "/api/product-categories", "/api/product-categories/**"
                        ).permitAll()
                        // Swagger & OpenAPI documentation
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // Protected authentication and profile endpoints
                        .requestMatchers("/api/v1/users/me/**", "/api/v1/users/me", "/api/v1/auth/me", "/api/auth/me", "/auth/me", "/api/me", "/users/me", "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout", "/api/auth/logout", "/auth/logout").authenticated()
                        // Admin Endpoints strictly restricted to ROLE_ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Any other endpoint requires authentication by default
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/oauth2/authorization")
                                .authorizationRequestRepository(httpCookieOAuth2AuthorizationRequestRepository)
                        )
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/login/oauth2/code/*")
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService)
                        )
                        .successHandler(oauth2AuthenticationSuccessHandler)
                        .failureHandler(oauth2AuthenticationFailureHandler)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedOriginPatterns(List.of("http://localhost:[*]", "http://127.0.0.1:[*]"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

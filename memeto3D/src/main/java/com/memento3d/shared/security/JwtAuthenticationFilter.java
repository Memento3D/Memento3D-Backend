package com.memento3d.shared.security;

import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.security.CustomUserDetailsService;
import com.memento3d.identity.infrastructure.security.JwtService;

import com.memento3d.identity.application.service.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = null;
        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7).trim();
        } else if (request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            jwt = request.getParameter("token").trim();
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Check if the token has been blacklisted (e.g. via logout)
        if (tokenBlacklistService.isBlacklisted(jwt)) {
            log.warn("Blocked request using blacklisted/revoked JWT: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Validate token and authenticate user
        try {
            final String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT processing failed for request {}: {}", request.getRequestURI(), ex.getMessage());
            // Proceed without setting security context; Spring Security's entry point will handle 401
        }

        filterChain.doFilter(request, response);
    }
}

package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.*;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.EmailAlreadyExistsException;
import com.memeto.team.memeto3d.exception.InvalidTokenException;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.security.JwtService;
import com.memeto.team.memeto3d.service.AuthService;
import com.memeto.team.memeto3d.service.RefreshTokenService;
import com.memeto.team.memeto3d.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration failed: Email {} is already registered", normalizedEmail);
            throw new EmailAlreadyExistsException("Email is already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(Role.ROLE_USER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Successfully registered user with id: {} and email: {}", savedUser.getId(), savedUser.getEmail());

        return RegisterResponse.builder()
                .message("Register successfully")
                .user(UserResponse.fromEntity(savedUser))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        log.info("User {} successfully authenticated with Redis refresh token", normalizedEmail);

        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new InvalidTokenException("Refresh token is required");
        }

        UUID userId = refreshTokenService.validateRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid, expired, or revoked refresh token"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("Successfully refreshed access token for user ID: {}", userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationInSeconds())
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }

        String email;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public LogoutResponse logout(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7).trim();
            try {
                Date expiration = jwtService.extractExpiration(token);
                tokenBlacklistService.blacklistToken(token, expiration.toInstant());
                log.info("Token successfully blacklisted in Redis on logout");
            } catch (Exception e) {
                log.warn("Could not extract expiration from token on logout: {}", e.getMessage());
                tokenBlacklistService.blacklistToken(token, Instant.now().plusSeconds(3600));
            }

            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof UserDetails userDetails) {
                    userRepository.findByEmail(userDetails.getUsername())
                            .ifPresent(u -> refreshTokenService.revokeUserRefreshTokens(u.getId()));
                }
            } catch (Exception e) {
                log.warn("Could not revoke refresh tokens on logout: {}", e.getMessage());
            }
        }

        SecurityContextHolder.clearContext();

        return LogoutResponse.builder()
                .message("Logout successfully")
                .details("JWT is stateless; token blacklisted and refresh tokens revoked in Redis.")
                .build();
    }
}

package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.entity.RefreshToken;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.RefreshTokenRepository;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class DatabaseRefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.auth.refresh-token-ttl-days:7}")
    private long refreshTokenTtlDays;

    @Override
    @Transactional
    public String createRefreshToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        return createRefreshToken(user);
    }

    @Override
    @Transactional
    public String createRefreshToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null when generating refresh token");
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Generated persistent database refresh token for user {} expiring at {}", user.getId(), expiresAt);
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> validateRefreshToken(String refreshToken) {
        return findByToken(refreshToken)
                .filter(token -> !token.isRevoked())
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .map(token -> token.getUser().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByToken(refreshToken);
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token in database: {}", refreshToken);
        });
    }

    @Override
    @Transactional
    public void revokeUserRefreshTokens(UUID userId) {
        if (userId == null) {
            return;
        }

        userRepository.findById(userId).ifPresent(user -> {
            List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(user);
            activeTokens.forEach(token -> token.setRevoked(true));
            refreshTokenRepository.saveAll(activeTokens);
            log.info("Revoked all active database refresh tokens for user {}", userId);
        });
    }

    @Override
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
        log.info("Cleaned up expired refresh tokens from database");
    }
}

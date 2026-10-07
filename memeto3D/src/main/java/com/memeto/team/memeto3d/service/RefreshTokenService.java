package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.entity.RefreshToken;
import com.memeto.team.memeto3d.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {
    String createRefreshToken(UUID userId);
    String createRefreshToken(User user);
    Optional<UUID> validateRefreshToken(String refreshToken);
    Optional<RefreshToken> findByToken(String refreshToken);
    void revokeRefreshToken(String refreshToken);
    void revokeUserRefreshTokens(UUID userId);
    void deleteExpiredTokens();
}

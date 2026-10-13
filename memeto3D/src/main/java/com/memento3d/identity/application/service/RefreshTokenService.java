package com.memento3d.identity.application.service;

import com.memento3d.identity.domain.model.RefreshToken;
import com.memento3d.identity.domain.model.User;

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

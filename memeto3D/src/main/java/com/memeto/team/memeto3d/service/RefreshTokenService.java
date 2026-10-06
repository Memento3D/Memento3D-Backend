package com.memeto.team.memeto3d.service;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {
    String createRefreshToken(UUID userId);
    Optional<UUID> validateRefreshToken(String refreshToken);
    void revokeRefreshToken(String refreshToken);
    void revokeUserRefreshTokens(UUID userId);
}

package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.UserResponse;

import java.util.Optional;
import java.util.UUID;

public interface UserCacheService {
    Optional<UserResponse> getUserFromCache(UUID userId);
    void cacheUser(UUID userId, UserResponse userResponse);
    void evictUser(UUID userId);
}

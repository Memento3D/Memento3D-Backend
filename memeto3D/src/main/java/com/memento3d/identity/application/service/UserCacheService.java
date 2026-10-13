package com.memento3d.identity.application.service;

import com.memento3d.identity.api.response.UserResponse;

import java.util.Optional;
import java.util.UUID;

public interface UserCacheService {
    Optional<UserResponse> getUserFromCache(UUID userId);
    void cacheUser(UUID userId, UserResponse userResponse);
    void evictUser(UUID userId);
}

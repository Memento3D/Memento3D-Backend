package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.UserResponse;

import java.util.UUID;

public interface UserService {
    /**
     * Retrieves user profile by ID using Redis cache first, falling back to PostgreSQL DB.
     * Cache key: user:{userId}
     */
    UserResponse getUserById(UUID userId);
}

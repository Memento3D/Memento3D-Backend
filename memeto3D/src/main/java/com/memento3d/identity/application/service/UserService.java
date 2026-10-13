package com.memento3d.identity.application.service;

import com.memento3d.identity.domain.model.User;

import com.memento3d.identity.api.response.UserResponse;

import java.util.UUID;

public interface UserService {
    /**
     * Retrieves user profile by ID using Redis cache first, falling back to PostgreSQL DB.
     * Cache key: user:{userId}
     */
    UserResponse getUserById(UUID userId);
}

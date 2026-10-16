package com.memento3d.identity.application.service;

import com.memento3d.identity.api.request.AdminCreateUserRequest;
import com.memento3d.identity.api.request.AdminUpdateUserRequest;
import com.memento3d.identity.api.request.UpdateProfileRequest;
import com.memento3d.identity.api.response.UserListResponse;
import com.memento3d.identity.api.response.UserResponse;

import java.util.UUID;

public interface UserService {
    /**
     * Retrieves user profile by ID using Redis cache first, falling back to PostgreSQL DB.
     * Cache key: user:{userId}
     */
    UserResponse getUserById(UUID userId);

    /**
     * Updates profile of currently authenticated user.
     */
    UserResponse updateMyProfile(UUID userId, UpdateProfileRequest request);

    /**
     * Lists users with pagination and optional search filter (Admin/Operator).
     */
    UserListResponse getUsers(String search, int page, int limit);

    /**
     * Admin creates a new user account.
     */
    UserResponse adminCreateUser(AdminCreateUserRequest request);

    /**
     * Admin updates user account role, quota, or status.
     */
    UserResponse adminUpdateUser(UUID userId, AdminUpdateUserRequest request);

    /**
     * Admin soft-deletes or deactivates a user.
     */
    void deleteUser(UUID userId);
}


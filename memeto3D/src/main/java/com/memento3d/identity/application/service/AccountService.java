package com.memento3d.identity.application.service;

import com.memento3d.identity.api.request.ChangePasswordRequest;
import com.memento3d.identity.api.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Self-service account security operations for the authenticated user.
 */
public interface AccountService {

    /**
     * Changes the local password after verifying the current one. Revokes refresh tokens, blacklists
     * the presented access token, invalidates older access tokens and notifies the owner by email.
     *
     * @param accessToken the bearer token used for this request (may be null)
     */
    void changePassword(UUID userId, ChangePasswordRequest request, String accessToken);

    /** Validates (size, MIME and magic bytes) and stores a new avatar. */
    UserResponse uploadAvatar(UUID userId, MultipartFile file);

    /** Removes the avatar so the UI falls back to the default placeholder. */
    UserResponse removeAvatar(UUID userId);
}

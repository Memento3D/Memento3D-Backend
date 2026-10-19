package com.memento3d.identity.application.service;

import java.util.UUID;

/**
 * Single place that mutates a user's password. Shared by "reset password" and "change password"
 * so both flows revoke sessions and audit identically.
 */
public interface CredentialService {

    /**
     * Encodes and stores the new password, stamps {@code passwordChangedAt} (which invalidates every
     * access token issued earlier), revokes all refresh tokens, evicts the user cache and writes a
     * security audit entry (never containing any password material).
     *
     * @param auditAction e.g. {@code PASSWORD_RESET} or {@code PASSWORD_CHANGED}
     */
    void updatePassword(UUID userId, String rawPassword, String auditAction);
}

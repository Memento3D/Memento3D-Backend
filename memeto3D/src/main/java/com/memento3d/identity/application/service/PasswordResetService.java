package com.memento3d.identity.application.service;

/**
 * Forgot-password / reset-password use cases (token kept in Redis as a SHA-256 hash with TTL).
 */
public interface PasswordResetService {

    /**
     * Starts a reset. Always completes silently for unknown / ineligible emails so callers cannot
     * enumerate accounts; only abuse limits surface as {@code 429 RATE_LIMIT_EXCEEDED}.
     */
    void requestPasswordReset(String email, String clientIp);

    /**
     * Completes a reset with the one-time token. The token is consumed atomically (single use,
     * safe under concurrent requests).
     */
    void resetPassword(String token, String newPassword, String clientIp);
}

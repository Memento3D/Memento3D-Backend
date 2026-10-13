package com.memento3d.identity.application.service;

import java.time.Instant;

/**
 * Service to manage invalidated/blacklisted JWT tokens.
 * By abstracting this as an interface, we can switch from database-backed storage
 * to Redis cache seamlessly without changing client or filter code.
 */
public interface TokenBlacklistService {

    /**
     * Adds the specified token to the blacklist with an expiry date.
     *
     * @param token      the JWT token string to revoke
     * @param expiryDate the instant when the token naturally expires
     */
    void blacklistToken(String token, Instant expiryDate);

    /**
     * Checks if the specified token is currently blacklisted.
     *
     * @param token the JWT token string
     * @return true if the token is revoked, false otherwise
     */
    boolean isBlacklisted(String token);
}

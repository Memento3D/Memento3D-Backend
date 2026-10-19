package com.memento3d.notification.service;

/**
 * Outbound transactional email gateway (account security flows).
 * <p>
 * Implementations MUST be non-blocking for the caller and MUST NOT throw: a mail-provider
 * outage must never change the observable behaviour of security endpoints (which would
 * otherwise leak account existence).
 */
public interface EmailService {

    /** Sends the password reset link. {@code resetLink} is a secret and must never be logged. */
    void sendPasswordResetEmail(String to, String fullName, String resetLink, int ttlMinutes);

    /** Security notice sent after a password was reset or changed. */
    void sendPasswordChangedNotification(String to, String fullName);

    /** Sent instead of a reset link when the account only signs in through Google. */
    void sendGoogleAccountNotice(String to, String fullName);
}

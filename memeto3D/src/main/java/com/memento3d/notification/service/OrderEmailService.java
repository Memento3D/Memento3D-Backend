package com.memento3d.notification.service;

import java.util.UUID;

/**
 * Service for delivering order lifecycle email notifications.
 */
public interface OrderEmailService {

    /**
     * Sends the order confirmation email for Event 1 (ORDER_CONFIRMED).
     * Strictly idempotent: will skip if already successfully sent.
     */
    void processOrderConfirmed(UUID orderId);

    /**
     * Sends the order completed email for Event 2 (ORDER_COMPLETED).
     * Strictly idempotent: will skip if already successfully sent.
     */
    void processOrderCompleted(UUID orderId);

    /**
     * Background retry job for emails that previously encountered transient SMTP errors.
     */
    void retryFailedEmails();
}

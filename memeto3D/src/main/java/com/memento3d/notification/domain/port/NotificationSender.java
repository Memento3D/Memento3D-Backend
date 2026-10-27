package com.memento3d.notification.domain.port;

import java.util.Map;
import java.util.UUID;

/**
 * Port interface for decoupled notifications (In-app, Email, Webhook, SMS).
 * Ensures notification failures do NOT rollback core business transactions.
 */
public interface NotificationSender {

    void sendNotification(UUID userId, String eventType, String title, String message, Map<String, Object> metadata);

    void sendEmail(String recipientEmail, String subject, String content);
}

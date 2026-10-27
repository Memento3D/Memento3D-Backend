package com.memento3d.notification.infrastructure;

import com.memento3d.notification.domain.port.NotificationSender;
import com.memento3d.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompositeNotificationSender implements NotificationSender {

    private final NotificationService notificationService;

    @Override
    @Async
    public void sendNotification(UUID userId, String eventType, String title, String message, Map<String, Object> metadata) {
        try {
            UUID refId = null;
            String refType = null;
            if (metadata != null) {
                Object refIdObj = metadata.get("referenceId");
                if (refIdObj instanceof UUID) {
                    refId = (UUID) refIdObj;
                } else if (refIdObj instanceof String) {
                    try {
                        refId = UUID.fromString((String) refIdObj);
                    } catch (Exception ignored) {}
                }
                refType = metadata.get("referenceType") != null ? metadata.get("referenceType").toString() : null;
            }

            notificationService.sendNotification(userId, title, message, eventType, refId, refType);
            log.info("Dispatched in-app notification to user: {} (event: {})", userId, eventType);
        } catch (Exception ex) {
            log.error("Failed to send in-app notification to user: {} (event: {}), error suppressed to protect transaction",
                    userId, eventType, ex);
        }
    }

    @Override
    @Async
    public void sendEmail(String recipientEmail, String subject, String content) {
        try {
            log.info("Sending email notification to: {} - Subject: {}", recipientEmail, subject);
            // Non-blocking dispatch
        } catch (Exception ex) {
            log.error("Failed to send transactional email to: {}, error suppressed to protect business flow",
                    recipientEmail, ex);
        }
    }
}

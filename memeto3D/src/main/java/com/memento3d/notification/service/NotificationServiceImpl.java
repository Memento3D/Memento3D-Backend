package com.memento3d.notification.service;

import com.memento3d.notification.domain.model.Notification;
import com.memento3d.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;

    @Override
    @Transactional
    public Notification sendNotification(UUID userId, String title, String message, String type, UUID referenceId, String referenceType) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .isRead(false)
                .build();
        Notification saved = notificationRepository.save(notification);
        log.info("Notification sent to user {}: type={}, id={}", userId, type, saved.getId());

        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("notificationId", saved.getId());
            payload.put("userId", userId);
            payload.put("title", title);
            payload.put("message", message);
            payload.put("type", type);
            payload.put("referenceId", referenceId != null ? referenceId.toString() : null);
            payload.put("referenceType", referenceType);
            payload.put("createdAt", saved.getCreatedAt());
            outboxPublisher.publish("NOTIFICATION", saved.getId().toString(), "NOTIFICATION_CREATED", payload);
        } catch (Exception e) {
            log.warn("Failed to publish outbox event for notification {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot access other user's notification");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }
}

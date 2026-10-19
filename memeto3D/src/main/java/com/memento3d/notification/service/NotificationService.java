package com.memento3d.notification.service;

import com.memento3d.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    Notification sendNotification(UUID userId, String title, String message, String type, UUID referenceId, String referenceType);

    Page<Notification> getUserNotifications(UUID userId, Pageable pageable);

    long getUnreadCount(UUID userId);

    void markAsRead(UUID notificationId, UUID userId);

    void markAllAsRead(UUID userId);
}

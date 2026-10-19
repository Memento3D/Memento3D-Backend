package com.memento3d.notification.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.notification.domain.model.Notification;
import com.memento3d.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification center for reconstruction, order, and approval events")
public class NotificationController {

    private final NotificationService notificationService;

    @Getter
    @Setter
    @Builder
    public static class NotificationResponse {
        private UUID id;
        private UUID userId;
        private String title;
        private String message;
        private String type;
        private UUID referenceId;
        private String referenceType;
        private boolean isRead;
        private Instant createdAt;

        public static NotificationResponse from(Notification n) {
            return NotificationResponse.builder()
                    .id(n.getId())
                    .userId(n.getUserId())
                    .title(n.getTitle())
                    .message(n.getMessage())
                    .type(n.getType())
                    .referenceId(n.getReferenceId())
                    .referenceType(n.getReferenceType())
                    .isRead(n.isRead())
                    .createdAt(n.getCreatedAt())
                    .build();
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user notifications")
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<NotificationResponse> page = notificationService.getUserNotifications(currentUser.getId(), pageable)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        long count = notificationService.getUnreadCount(currentUser.getId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark single notification as read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id) {
        notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}

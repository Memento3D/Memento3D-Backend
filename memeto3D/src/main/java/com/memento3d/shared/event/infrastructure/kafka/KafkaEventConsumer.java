package com.memento3d.shared.event.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.notification.service.NotificationService;
import com.memento3d.shared.event.domain.model.ProcessedEvent;
import com.memento3d.shared.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.memento3d.shared.event.infrastructure.sse.SseEmitterManager;
import com.memento3d.shared.event.model.RealTimeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final SseEmitterManager sseEmitterManager;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.consumer.group-id:memento3d-backend-group}")
    private String consumerGroupId;

    @KafkaListener(
            topics = {
                    "${app.kafka.topics.reconstruction:memento3d.reconstruction}",
                    "${app.kafka.topics.orders:memento3d.orders}",
                    "${app.kafka.topics.payments:memento3d.payments}",
                    "${app.kafka.topics.printjobs:memento3d.printjobs}",
                    "${app.kafka.topics.quality:memento3d.quality}",
                    "${app.kafka.topics.notifications:memento3d.notifications}"
            },
            groupId = "${spring.kafka.consumer.group-id:memento3d-backend-group}",
            autoStartup = "${app.kafka.enabled:true}"
    )
    @Transactional
    public void consumeEvent(String message) {
        try {
            RealTimeEvent event = objectMapper.readValue(message, RealTimeEvent.class);
            if (event.getEventId() == null) {
                log.warn("Received Kafka event with null eventId, skipping");
                return;
            }

            String eventIdStr = event.getEventId().toString();

            // Idempotency check: deduplicate event by eventId
            if (processedEventRepository.existsById(eventIdStr)) {
                log.debug("Event {} already processed by consumer group {}, skipping duplicate", eventIdStr, consumerGroupId);
                return;
            }

            // Mark as processed
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventIdStr)
                    .consumerGroup(consumerGroupId)
                    .processedAt(Instant.now())
                    .build());

            log.info("Kafka consumer processed event: id={}, type={}, resource={}/{}",
                    eventIdStr, event.getType(), event.getResourceType(), event.getResourceId());

            // 1. Dispatch event to SSE clients via gateway
            sseEmitterManager.dispatch(event);

            // 2. Automatically generate persistent notifications for critical milestones
            createNotificationIfNeeded(event);

        } catch (Exception e) {
            log.error("Failed to process Kafka message: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing Kafka event", e);
        }
    }

    private void createNotificationIfNeeded(RealTimeEvent event) {
        if (event == null || event.getTargetUserId() == null) return;

        UUID targetUserId = event.getTargetUserId();
        UUID refId = null;
        try {
            if (event.getResourceId() != null) {
                refId = UUID.fromString(event.getResourceId());
            }
        } catch (Exception ignored) {}

        switch (event.getType()) {
            case "PAYMENT_STATUS_CHANGED" -> {
                Object status = event.getData() != null ? event.getData().get("paymentStatus") : null;
                if ("PAID".equals(status) || "SUCCESS".equals(status)) {
                    notificationService.sendNotification(
                            targetUserId,
                            "Thanh toán thành công",
                            "Đơn hàng của bạn đã được xác nhận thanh toán thành công.",
                            "PAYMENT_SUCCESS",
                            refId,
                            "ORDER"
                    );
                }
            }
            case "PREVIEW_READY" -> notificationService.sendNotification(
                    targetUserId,
                    "Mô hình 3D Preview đã sẵn sàng",
                    "Mô hình 3D xem trước của bạn đã dựng xong. Hãy mở xem ngay!",
                    "PREVIEW_READY",
                    refId,
                    "RECONSTRUCTION"
            );
            case "MODEL_APPROVAL_REQUIRED" -> notificationService.sendNotification(
                    targetUserId,
                    "Mô hình 3D chất lượng cao cần duyệt",
                    "Mô hình 3D chi tiết cao của bạn đã hoàn thành. Hãy kiểm tra và duyệt để xưởng tiến hành in.",
                    "MODEL_APPROVAL_REQUIRED",
                    refId,
                    "MODEL_VERSION"
            );
            case "DIGITAL_ASSET_READY" -> notificationService.sendNotification(
                    targetUserId,
                    "File 3D kỹ thuật số sẵn sàng tải",
                    "File mô hình 3D chất lượng cao (.GLB) của bạn đã sẵn sàng trong thư viện.",
                    "DIGITAL_ASSET_READY",
                    refId,
                    "DIGITAL_ASSET"
            );
            default -> {}
        }
    }
}

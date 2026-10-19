package com.memento3d.shared.event.infrastructure.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.shared.event.infrastructure.kafka.KafkaEventPublisher;
import com.memento3d.shared.event.infrastructure.sse.SseEmitterManager;
import com.memento3d.shared.event.model.RealTimeEvent;
import com.memento3d.shared.outbox.domain.model.TransactionalOutbox;
import com.memento3d.shared.outbox.infrastructure.persistence.repository.TransactionalOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    private final TransactionalOutboxRepository outboxRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final SseEmitterManager sseEmitterManager;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Value("${app.kafka.topics.reconstruction:memento3d.reconstruction}")
    private String reconstructionTopic;

    @Value("${app.kafka.topics.orders:memento3d.orders}")
    private String ordersTopic;

    @Value("${app.kafka.topics.payments:memento3d.payments}")
    private String paymentsTopic;

    @Value("${app.kafka.topics.printjobs:memento3d.printjobs}")
    private String printjobsTopic;

    @Value("${app.kafka.topics.quality:memento3d.quality}")
    private String qualityTopic;

    @Value("${app.kafka.topics.notifications:memento3d.notifications}")
    private String notificationsTopic;

    @Value("${app.kafka.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${app.kafka.outbox.max-retries:5}")
    private int maxRetries = 5;

    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval-ms:1000}")
    @Transactional
    public void relayOutboxMessages() {
        List<TransactionalOutbox> pendingEvents =
                outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, batchSize));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to relay", pendingEvents.size());

        for (TransactionalOutbox outbox : pendingEvents) {
            try {
                RealTimeEvent event = mapToRealTimeEvent(outbox);
                String topic = resolveTopic(outbox.getAggregateType(), outbox.getEventType());

                if (kafkaEnabled) {
                    try {
                        kafkaEventPublisher.publish(topic, event);
                    } catch (Exception kafkaEx) {
                        log.warn("Kafka broker unavailable for topic={}, falling back to direct SSE dispatch: {}", topic, kafkaEx.getMessage());
                        sseEmitterManager.dispatch(event);
                    }
                } else {
                    // Fallback directly to SSE when Kafka is disabled
                    sseEmitterManager.dispatch(event);
                }

                outbox.setStatus("PROCESSED");
                outbox.setProcessedAt(Instant.now());
                outboxRepository.save(outbox);
            } catch (Exception ex) {
                log.error("Failed to relay outbox event ID={}: {}", outbox.getId(), ex.getMessage());
                int retries = (outbox.getRetryCount() != null ? outbox.getRetryCount() : 0) + 1;
                outbox.setRetryCount(retries);
                if (retries >= maxRetries) {
                    outbox.setStatus("FAILED");
                    log.error("Outbox event ID={} reached max retries, marked FAILED", outbox.getId());
                }
                outboxRepository.save(outbox);
            }
        }
    }

    private RealTimeEvent mapToRealTimeEvent(TransactionalOutbox outbox) {
        Map<String, Object> data = Collections.emptyMap();
        try {
            if (outbox.getPayload() != null && !outbox.getPayload().isBlank()) {
                data = objectMapper.readValue(outbox.getPayload(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("Could not parse outbox payload for event {}: {}", outbox.getId(), e.getMessage());
        }

        UUID targetUserId = null;
        if (data.containsKey("userId") && data.get("userId") != null) {
            try {
                targetUserId = UUID.fromString(data.get("userId").toString());
            } catch (Exception ignored) {}
        }

        String eventType = normalizeEventType(outbox.getEventType());

        return RealTimeEvent.builder()
                .eventId(outbox.getId())
                .type(eventType)
                .resourceType(outbox.getAggregateType())
                .resourceId(outbox.getAggregateId())
                .targetUserId(targetUserId)
                .occurredAt(outbox.getCreatedAt() != null ? outbox.getCreatedAt() : Instant.now())
                .data(data)
                .build();
    }

    private String normalizeEventType(String rawEventType) {
        if (rawEventType == null) return "UNKNOWN";
        return switch (rawEventType) {
            case "PREVIEW_JOB_CREATED", "HIGH_QUALITY_JOB_CREATED", "JOB_RETRIED", "RECONSTRUCTION_PROGRESS" -> "RECONSTRUCTION_UPDATED";
            case "PAYMENT_SUCCEEDED", "PAYMENT_UPDATED" -> "PAYMENT_STATUS_CHANGED";
            case "ORDER_CONFIRMED", "ORDER_STATUS_UPDATED", "ORDER_CANCELLED" -> "ORDER_STATUS_CHANGED";
            case "MODEL_APPROVED" -> "MODEL_APPROVED";
            case "REVISION_REQUESTED" -> "MODEL_REVISION_COMPLETED";
            case "QC_PASSED", "QC_FAILED" -> "QC_STATUS_CHANGED";
            case "PRINT_JOB_CREATED", "PRINT_JOB_STARTED", "PRINT_JOB_COMPLETED", "PRINT_JOB_FAILED" -> "PRINT_JOB_UPDATED";
            case "PRINTER_OFFLINE", "PRINTER_ONLINE", "PRINTER_STATUS_UPDATED" -> "PRINTER_STATUS_CHANGED";
            case "ENTITLEMENT_GRANTED" -> "ENTITLEMENT_GRANTED";
            case "DIGITAL_ASSET_READY" -> "DIGITAL_ASSET_READY";
            default -> rawEventType;
        };
    }

    private String resolveTopic(String aggregateType, String eventType) {
        if (aggregateType == null) return ordersTopic;

        String upper = aggregateType.toUpperCase();
        if (upper.contains("RECONSTRUCTION")) {
            return reconstructionTopic;
        } else if (upper.contains("ORDER")) {
            return ordersTopic;
        } else if (upper.contains("PAYMENT")) {
            return paymentsTopic;
        } else if (upper.contains("PRINT")) {
            return printjobsTopic;
        } else if (upper.contains("QUALITY") || upper.contains("QC")) {
            return qualityTopic;
        } else if (upper.contains("NOTIFICATION")) {
            return notificationsTopic;
        } else if (upper.contains("MODEL")) {
            return ordersTopic;
        }
        return ordersTopic;
    }
}

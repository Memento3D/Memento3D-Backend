package com.memento3d.shared.event.infrastructure.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.shared.event.model.RealTimeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseEmitterManager {

    private final ObjectMapper objectMapper;
    private final long sseTimeoutMs;

    // Map: userId -> Map<connectionId, ClientSession>
    private final Map<UUID, Map<String, ClientSession>> clientSessions = new ConcurrentHashMap<>();

    public SseEmitterManager(
            ObjectMapper objectMapper,
            @Value("${app.realtime.sse.timeout-ms:300000}") long sseTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.sseTimeoutMs = sseTimeoutMs;
    }

    public record ClientSession(String connectionId, UUID userId, Role role, SseEmitter emitter) {}

    public SseEmitter createConnection(UUID userId, Role role) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);

        ClientSession session = new ClientSession(connectionId, userId, role, emitter);

        clientSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(connectionId, session);

        emitter.onCompletion(() -> removeConnection(userId, connectionId, "completed"));
        emitter.onTimeout(() -> {
            removeConnection(userId, connectionId, "timeout");
            emitter.complete();
        });
        emitter.onError(ex -> removeConnection(userId, connectionId, "error: " + ex.getMessage()));

        // Send initial connection handshake event
        try {
            RealTimeEvent welcomeEvent = RealTimeEvent.builder()
                    .eventId(UUID.randomUUID())
                    .type("CONNECTION_ESTABLISHED")
                    .resourceType("GATEWAY")
                    .resourceId(connectionId)
                    .targetUserId(userId)
                    .occurredAt(Instant.now())
                    .data(Map.of(
                            "status", "CONNECTED",
                            "userId", userId.toString(),
                            "role", role.name()
                    ))
                    .build();

            emitter.send(SseEmitter.event()
                    .id(welcomeEvent.getEventId().toString())
                    .name(welcomeEvent.getType())
                    .data(objectMapper.writeValueAsString(welcomeEvent)));
            log.info("SSE connection opened for userId={}, role={}, connectionId={}", userId, role, connectionId);
        } catch (IOException e) {
            log.warn("Failed to send welcome SSE event to user {}: {}", userId, e.getMessage());
        }

        return emitter;
    }

    private void removeConnection(UUID userId, String connectionId, String reason) {
        Map<String, ClientSession> userSessions = clientSessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(connectionId);
            if (userSessions.isEmpty()) {
                clientSessions.remove(userId);
            }
        }
        log.debug("SSE connection closed for userId={}, connectionId={}, reason={}", userId, connectionId, reason);
    }

    public void dispatch(RealTimeEvent event) {
        if (event == null) return;

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize RealTimeEvent: {}", e.getMessage());
            return;
        }

        UUID targetUserId = event.getTargetUserId();
        boolean isStaffEvent = isEventRelevantForStaff(event.getType());

        Set<ClientSession> recipients = new HashSet<>();

        // 1. If targeted to a specific user, add all their active connections
        if (targetUserId != null) {
            Map<String, ClientSession> userSessions = clientSessions.get(targetUserId);
            if (userSessions != null) {
                recipients.addAll(userSessions.values());
            }
        }

        // 2. If event is relevant to staff or has no specific customer target, include staff
        if (isStaffEvent || targetUserId == null) {
            for (Map<String, ClientSession> userSessions : clientSessions.values()) {
                for (ClientSession session : userSessions.values()) {
                    if (isStaffRole(session.role())) {
                        recipients.add(session);
                    }
                }
            }
        }

        // 3. Send event to all resolved recipients
        for (ClientSession session : recipients) {
            try {
                session.emitter().send(SseEmitter.event()
                        .id(event.getEventId() != null ? event.getEventId().toString() : UUID.randomUUID().toString())
                        .name(event.getType())
                        .data(jsonPayload));
            } catch (IOException ex) {
                log.debug("Removing failed emitter for user {}", session.userId());
                removeConnection(session.userId(), session.connectionId(), "send-failed");
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.realtime.sse.heartbeat-interval-ms:15000}")
    public void sendHeartbeat() {
        if (clientSessions.isEmpty()) {
            return;
        }

        List<ClientSession> deadSessions = new ArrayList<>();

        for (Map<String, ClientSession> userSessions : clientSessions.values()) {
            for (ClientSession session : userSessions.values()) {
                try {
                    session.emitter().send(SseEmitter.event().comment("heartbeat:" + System.currentTimeMillis()));
                } catch (Exception e) {
                    deadSessions.add(session);
                }
            }
        }

        for (ClientSession dead : deadSessions) {
            removeConnection(dead.userId(), dead.connectionId(), "heartbeat-failed");
        }
    }

    private boolean isStaffRole(Role role) {
        return role == Role.ROLE_ADMIN
                || role == Role.ROLE_MANAGER
                || role == Role.ROLE_OPERATOR
                || role == Role.ROLE_WAREHOUSE;
    }

    private boolean isEventRelevantForStaff(String eventType) {
        if (eventType == null) return false;
        return switch (eventType) {
            case "ORDER_STATUS_CHANGED", "PAYMENT_STATUS_CHANGED", "NEW_ORDER_CREATED",
                 "MODEL_APPROVAL_REQUIRED", "MODEL_REVISION_COMPLETED", "REVISION_REQUESTED", "MODEL_APPROVED",
                 "PRINT_JOB_UPDATED", "PRINTER_STATUS_CHANGED", "QC_STATUS_CHANGED",
                 "STOCK_UPDATED", "QUOTATION_SUBMITTED" -> true;
            default -> false;
        };
    }

    public int getActiveConnectionCount() {
        return clientSessions.values().stream().mapToInt(Map::size).sum();
    }
}

package com.memento3d.shared.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.shared.event.api.RealTimeEventsController;
import com.memento3d.shared.event.infrastructure.sse.SseEmitterManager;
import com.memento3d.shared.event.model.RealTimeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealTimeEventsTest {

    @Mock
    private SseEmitterManager sseEmitterManager;

    private RealTimeEventsController controller;

    private UUID userId;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        controller = new RealTimeEventsController(sseEmitterManager);
        userId = UUID.randomUUID();
        User mockUser = User.builder()
                .id(userId)
                .email("customer@memento3d.vn")
                .password("hash")
                .role(Role.ROLE_USER)
                .build();
        userDetails = new CustomUserDetails(mockUser);
    }

    @Test
    @DisplayName("streamEvents: connects authenticated user and returns active SseEmitter")
    void streamEvents_success() {
        SseEmitter mockEmitter = new SseEmitter(60000L);
        when(sseEmitterManager.createConnection(userId, Role.ROLE_USER)).thenReturn(mockEmitter);

        SseEmitter result = controller.streamEvents(userDetails);

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(mockEmitter);
        verify(sseEmitterManager, times(1)).createConnection(userId, Role.ROLE_USER);
    }

    @Test
    @DisplayName("SseEmitterManager: register connection, dispatch customer event, and clean up")
    void sseEmitterManager_lifecycle() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SseEmitterManager manager = new SseEmitterManager(objectMapper, 60000L);

        UUID customerId = UUID.randomUUID();
        SseEmitter emitter = manager.createConnection(customerId, Role.ROLE_USER);

        assertThat(emitter).isNotNull();
        assertThat(manager.getActiveConnectionCount()).isGreaterThanOrEqualTo(1);

        // Dispatch customer-scoped event
        RealTimeEvent customerEvent = RealTimeEvent.builder()
                .eventId(UUID.randomUUID())
                .type("ORDER_STATUS_CHANGED")
                .resourceType("ORDER")
                .resourceId(UUID.randomUUID().toString())
                .targetUserId(customerId)
                .occurredAt(Instant.now())
                .data(Map.of("status", "CONFIRMED"))
                .build();

        // Should dispatch smoothly
        manager.dispatch(customerEvent);

        // Disconnecting / completing emitter
        emitter.complete();
    }
}

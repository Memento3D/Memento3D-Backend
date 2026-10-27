package com.memento3d.shared.event.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.shared.event.infrastructure.sse.SseEmitterManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping({"/api/v1/events", "/events", "/api/events"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Real-Time Gateway", description = "Server-Sent Events gateway for real-time status updates and notifications")
@SecurityRequirement(name = "bearerAuth")
public class RealTimeEventsController {

    private final SseEmitterManager sseEmitterManager;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Subscribe to live Server-Sent Events stream",
            description = "Establishes a persistent SSE stream for the authenticated user/staff member. Supports Bearer Authorization header or ?token= query parameter.")
    public SseEmitter streamEvents(@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Client connected to SSE stream: userId={}, role={}", userDetails.getId(), userDetails.getRole());
        return sseEmitterManager.createConnection(userDetails.getId(), userDetails.getRole());
    }
}

package com.memento3d.printers.application.service.impl;

import com.memento3d.printers.application.service.PrinterAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Marks agents (and their printers) OFFLINE when heartbeats stop.
 * Safe to run on several backend instances: updates are guarded by @Version.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.printing.agent.liveness-enabled", havingValue = "true", matchIfMissing = true)
public class PrinterAgentLivenessScheduler {

    private final PrinterAgentService agentService;

    @Value("${app.printing.agent.heartbeat-timeout-seconds:45}")
    private long heartbeatTimeoutSeconds;

    @Scheduled(
            fixedDelayString = "${app.printing.agent.liveness-check-interval-ms:15000}",
            initialDelayString = "${app.printing.agent.liveness-check-interval-ms:15000}")
    public void markSilentAgentsOffline() {
        try {
            agentService.markStaleAgentsOffline(Instant.now().minusSeconds(heartbeatTimeoutSeconds));
        } catch (Exception e) {
            // Optimistic-lock races with a concurrent heartbeat are expected; retry next tick.
            log.debug("Agent liveness check skipped: {}", e.getMessage());
        }
    }
}

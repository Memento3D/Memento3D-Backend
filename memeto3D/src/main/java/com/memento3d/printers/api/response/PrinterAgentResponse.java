package com.memento3d.printers.api.response;

import com.memento3d.printers.domain.model.PrinterAgentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Never exposes the token hash. */
@Getter
@Builder
public class PrinterAgentResponse {
    private UUID id;
    private String name;
    private String description;
    private PrinterAgentStatus status;
    private String agentVersion;
    private Instant lastHeartbeatAt;
    private Instant createdAt;
    private Instant updatedAt;
}

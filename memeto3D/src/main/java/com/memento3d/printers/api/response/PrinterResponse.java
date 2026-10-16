package com.memento3d.printers.api.response;

import com.memento3d.printers.domain.model.PrinterProvider;
import com.memento3d.printers.domain.model.PrinterStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PrinterResponse {
    private UUID id;
    private String name;
    private String serialNumber;
    private PrinterProvider provider;
    private String model;
    private PrinterStatus status;
    /** Convenience flag for the UI: false when the printer is OFFLINE. */
    private boolean online;
    private UUID agentId;
    private String agentName;
    private String octoprintPrinterId;
    private Instant lastSeenAt;
    private Instant createdAt;
    private Instant updatedAt;
}

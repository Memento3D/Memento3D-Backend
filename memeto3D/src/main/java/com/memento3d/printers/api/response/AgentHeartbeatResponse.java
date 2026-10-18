package com.memento3d.printers.api.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class AgentHeartbeatResponse {
    private Instant serverTime;
    private int pollIntervalSeconds;
    private int acceptedPrinters;
    /** octoprintPrinterIds the backend does not know for this agent (misconfiguration hint). */
    private List<String> unknownPrinterIds;
}

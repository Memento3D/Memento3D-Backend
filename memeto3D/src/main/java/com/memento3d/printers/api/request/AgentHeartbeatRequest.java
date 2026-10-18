package com.memento3d.printers.api.request;

import com.memento3d.printers.domain.model.PrinterStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Heartbeat sent by a Local Printer Agent every few seconds.
 * Phase 8 extends {@link PrinterReport} with telemetry fields (temperatures, progress...).
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentHeartbeatRequest {

    @Size(max = 50, message = "agentVersion must be at most 50 characters")
    private String agentVersion;

    @Valid
    @Size(max = 50, message = "at most 50 printers per heartbeat")
    private List<PrinterReport> printers = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PrinterReport {

        @NotBlank(message = "octoprintPrinterId is required")
        @Size(max = 100, message = "octoprintPrinterId must be at most 100 characters")
        private String octoprintPrinterId;

        @NotNull(message = "status is required")
        private PrinterStatus status;
    }
}

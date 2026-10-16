package com.memento3d.printers.application.mapper;

import com.memento3d.printers.api.response.PrinterAgentResponse;
import com.memento3d.printers.api.response.PrinterResponse;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterAgent;
import com.memento3d.printers.domain.model.PrinterStatus;
import org.springframework.stereotype.Component;

/**
 * Manual mapper (MapStruct is not a dependency of this project).
 * Must be called inside a transaction when the printer's lazy agent may be accessed.
 */
@Component
public class PrinterMapper {

    public PrinterResponse toResponse(Printer printer) {
        PrinterAgent agent = printer.getAgent();
        return PrinterResponse.builder()
                .id(printer.getId())
                .name(printer.getName())
                .serialNumber(printer.getSerialNumber())
                .provider(printer.getProvider())
                .model(printer.getModel())
                .status(printer.getStatus())
                .online(printer.getStatus() != PrinterStatus.OFFLINE)
                .agentId(agent != null ? agent.getId() : null)
                .agentName(agent != null ? agent.getName() : null)
                .octoprintPrinterId(printer.getOctoprintPrinterId())
                .lastSeenAt(printer.getLastSeenAt())
                .createdAt(printer.getCreatedAt())
                .updatedAt(printer.getUpdatedAt())
                .build();
    }

    public PrinterAgentResponse toResponse(PrinterAgent agent) {
        return PrinterAgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .status(agent.getStatus())
                .agentVersion(agent.getAgentVersion())
                .lastHeartbeatAt(agent.getLastHeartbeatAt())
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }
}

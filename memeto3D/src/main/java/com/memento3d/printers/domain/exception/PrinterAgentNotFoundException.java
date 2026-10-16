package com.memento3d.printers.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PrinterAgentNotFoundException extends BusinessException {
    public PrinterAgentNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "PRINTER_AGENT_NOT_FOUND", "Printer agent not found: " + id);
    }
}

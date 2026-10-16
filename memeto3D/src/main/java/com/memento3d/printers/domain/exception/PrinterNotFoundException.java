package com.memento3d.printers.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PrinterNotFoundException extends BusinessException {
    public PrinterNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "PRINTER_NOT_FOUND", "Printer not found: " + id);
    }
}

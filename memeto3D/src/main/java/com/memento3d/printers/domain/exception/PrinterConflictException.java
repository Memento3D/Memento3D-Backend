package com.memento3d.printers.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 409 – duplicate resource or operation not allowed in the current printer/agent state. */
public class PrinterConflictException extends BusinessException {
    public PrinterConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}

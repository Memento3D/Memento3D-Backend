package com.memento3d.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for domain/business exceptions that carry an HTTP status and a stable,
 * machine-readable error code (e.g. {@code INVALID_PRINT_JOB_STATE}).
 * <p>
 * {@code GlobalExceptionHandler} converts any subclass into the unified {@code ErrorResponse}
 * so new modules (printing, slicing, quoting...) do not need to touch the handler again.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

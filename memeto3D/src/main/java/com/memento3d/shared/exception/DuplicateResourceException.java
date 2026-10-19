package com.memento3d.shared.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }

    public DuplicateResourceException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}

package com.memento3d.reconstruction.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class QuotaExceededException extends BusinessException {
    public QuotaExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", message);
    }
}

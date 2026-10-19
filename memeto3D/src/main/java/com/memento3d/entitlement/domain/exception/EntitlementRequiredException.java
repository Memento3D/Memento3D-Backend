package com.memento3d.entitlement.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EntitlementRequiredException extends BusinessException {
    public EntitlementRequiredException(String message) {
        super(HttpStatus.FORBIDDEN, "ENTITLEMENT_REQUIRED", message);
    }
}

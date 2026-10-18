package com.memento3d.orders.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidOrderStatusException extends BusinessException {
    public InvalidOrderStatusException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATUS", message);
    }
}

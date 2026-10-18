package com.memento3d.orders.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class OrderAccessDeniedException extends BusinessException {
    public OrderAccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", message);
    }
}

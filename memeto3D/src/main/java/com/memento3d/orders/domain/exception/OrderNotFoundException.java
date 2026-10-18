package com.memento3d.orders.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", message);
    }
}

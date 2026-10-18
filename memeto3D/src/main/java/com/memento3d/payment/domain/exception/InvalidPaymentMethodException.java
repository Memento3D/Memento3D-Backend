package com.memento3d.payment.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidPaymentMethodException extends BusinessException {
    public InvalidPaymentMethodException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD", message);
    }
}

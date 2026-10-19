package com.memento3d.payment.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PaymentException extends BusinessException {
    public PaymentException(String message) {
        super(HttpStatus.BAD_REQUEST, "PAYMENT_ERROR", message);
    }

    public PaymentException(HttpStatus status, String errorCode, String message) {
        super(status, errorCode, message);
    }

    public PaymentException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, "PAYMENT_ERROR", message);
    }
}

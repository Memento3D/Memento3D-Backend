package com.memento3d.payment.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PaymentAlreadyPaidException extends BusinessException {
    public PaymentAlreadyPaidException(String message) {
        super(HttpStatus.BAD_REQUEST, "PAYMENT_ALREADY_PAID", message);
    }
}

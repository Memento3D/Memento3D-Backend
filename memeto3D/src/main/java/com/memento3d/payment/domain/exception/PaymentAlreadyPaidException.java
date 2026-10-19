package com.memento3d.payment.domain.exception;

import org.springframework.http.HttpStatus;

public class PaymentAlreadyPaidException extends PaymentException {
    public PaymentAlreadyPaidException(String message) {
        super(HttpStatus.BAD_REQUEST, "PAYMENT_ALREADY_PAID", message);
    }
}

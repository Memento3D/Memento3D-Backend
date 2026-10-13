package com.memento3d.payment.domain.exception;

public class PaymentAmountMismatchException extends PaymentException {
    public PaymentAmountMismatchException(String message) {
        super(message);
    }
}

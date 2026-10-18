package com.memento3d.payment.domain.model;

public enum PaymentStatus {
    PENDING,
    PAID,
    SUCCESS,
    PROCESSING,
    FAILED,
    CANCELLED,
    EXPIRED,
    REFUNDED,
    UNKNOWN;

    public boolean isPaid() {
        return this == PAID || this == SUCCESS;
    }
}

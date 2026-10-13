package com.memento3d.payment.domain.model;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    EXPIRED,
    UNKNOWN
}

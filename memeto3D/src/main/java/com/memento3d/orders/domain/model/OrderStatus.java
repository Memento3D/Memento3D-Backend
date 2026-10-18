package com.memento3d.orders.domain.model;

public enum OrderStatus {
    PENDING,
    PENDING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    PRINTING,
    READY,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDED
}

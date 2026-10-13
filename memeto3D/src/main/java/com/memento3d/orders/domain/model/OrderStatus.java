package com.memento3d.orders.domain.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PRINTING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}

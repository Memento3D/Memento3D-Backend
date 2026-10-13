package com.memento3d.orders.application.service;

import com.memento3d.orders.domain.model.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderService {
    Order getOrderById(UUID orderId);
    Order validateOrderForPayment(UUID orderId, UUID userId);
    void updateOrderStatusPaid(UUID orderId);
}

package com.memento3d.orders.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.api.request.CreateOrderRequest;
import com.memento3d.orders.api.request.UpdateOrderRequest;
import com.memento3d.orders.api.response.OrderListResponse;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.domain.model.Order;

import java.util.UUID;

public interface OrderService {
    Order getOrderById(UUID orderId);
    Order validateOrderForPayment(UUID orderId, UUID userId);
    void updateOrderStatusPaid(UUID orderId);

    OrderResponse createOrder(CreateOrderRequest request, UUID userId);
    OrderListResponse getUserOrders(UUID userId, Role userRole, int page, int limit);
    OrderResponse getOrderDetail(UUID orderId, UUID userId, Role userRole);
    OrderResponse updateOrder(UUID orderId, UpdateOrderRequest request, UUID userId, Role userRole);
    void cancelOrder(UUID orderId, UUID userId, Role userRole);
}

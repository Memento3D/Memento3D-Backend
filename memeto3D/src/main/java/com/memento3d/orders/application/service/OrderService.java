package com.memento3d.orders.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.api.request.CreateOrderRequest;
import com.memento3d.orders.api.request.UpdateOrderRequest;
import com.memento3d.orders.api.response.OrderListResponse;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;

import java.util.UUID;

public interface OrderService {

    Order getOrderById(UUID orderId);

    Order validateOrderForPayment(UUID orderId, UUID userId);

    void updateOrderStatusPaid(UUID orderId);

    OrderResponse createOrder(CreateOrderRequest request, UUID userId);

    default OrderListResponse getUserOrders(UUID userId, Role userRole, int page, int limit) {
        return getUserOrders(userId, userRole, null, null, page, limit);
    }

    OrderListResponse getUserOrders(UUID userId, Role userRole, String query, OrderStatus status, int page, int limit);

    default OrderListResponse getMyOrders(UUID userId, OrderStatus status, int page, int limit) {
        return getMyOrders(userId, null, status, page, limit);
    }

    OrderListResponse getMyOrders(UUID userId, String query, OrderStatus status, int page, int limit);

    OrderListResponse getManagerOrders(String query, OrderStatus status, int page, int limit);

    OrderResponse updateOrderStatus(UUID orderId, OrderStatus nextStatus, UUID managerId, Role role);

    OrderResponse getOrderDetail(UUID orderId, UUID userId, Role userRole);

    OrderResponse updateOrder(UUID orderId, UpdateOrderRequest request, UUID userId, Role userRole);

    void cancelOrder(UUID orderId, UUID userId, Role userRole);

    java.util.Map<String, Long> getOrderStatusCounts(UUID userId, Role role);

    OrderResponse reviewFeasibility(UUID orderId, com.memento3d.orders.api.request.OrderFeasibilityReviewRequest request, UUID managerId, Role role);

    OrderResponse acceptWorkshopQuotation(UUID orderId, UUID customerId);
}

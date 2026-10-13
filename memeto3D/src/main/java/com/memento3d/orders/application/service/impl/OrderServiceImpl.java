package com.memento3d.orders.application.service.impl;

import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public Order validateOrderForPayment(UUID orderId, UUID userId) {
        Order order = getOrderById(orderId);
        if (!order.getUserId().equals(userId)) {
            log.warn("Access denied: User {} does not own order {}", userId, orderId);
            throw new AccessDeniedException("User is not authorized to access this order");
        }
        if (!order.isEligibleForPayment()) {
            log.warn("Order {} is not eligible for payment. Current status: {}", orderId, order.getStatus());
            throw new IllegalStateException("Order status is not eligible for payment: " + order.getStatus());
        }
        return order;
    }

    @Override
    @Transactional
    public void updateOrderStatusPaid(UUID orderId) {
        Order order = getOrderById(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} is already confirmed/paid", orderId);
            return;
        }
        order.markAsPaid();
        orderRepository.save(order);
        log.info("Order {} status updated to CONFIRMED", orderId);
    }
}

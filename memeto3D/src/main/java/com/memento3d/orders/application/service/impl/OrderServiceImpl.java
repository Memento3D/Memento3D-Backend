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

    @Override
    @Transactional
    public com.memento3d.orders.api.response.OrderResponse createOrder(com.memento3d.orders.api.request.CreateOrderRequest request, UUID userId) {
        Order order = Order.builder()
                .userId(userId)
                .scanId(request.getScanId())
                .totalPrice(request.getTotalPrice())
                .currency(request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency() : "VND")
                .status(OrderStatus.PENDING_PAYMENT)
                .recipientName(request.getRecipientName())
                .shippingAddress(request.getShippingAddress())
                .phone(request.getPhone())
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created successfully: id={}, user={}, total={}", saved.getId(), userId, saved.getTotalPrice());
        return com.memento3d.orders.api.response.OrderResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public com.memento3d.orders.api.response.OrderListResponse getUserOrders(UUID userId, com.memento3d.identity.domain.model.Role userRole, int page, int limit) {
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = limit > 0 ? Math.min(limit, 100) : 20;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(pageIndex, pageSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        org.springframework.data.domain.Page<Order> orderPage;
        boolean isStaff = (userRole == com.memento3d.identity.domain.model.Role.ROLE_ADMIN 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_MANAGER 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_OPERATOR);
        if (isStaff) {
            orderPage = orderRepository.findAll(pageable);
        } else {
            orderPage = orderRepository.findByUserId(userId, pageable);
        }

        return com.memento3d.orders.api.response.OrderListResponse.builder()
                .orders(orderPage.getContent().stream().map(com.memento3d.orders.api.response.OrderResponse::fromEntity).toList())
                .page(page)
                .limit(pageSize)
                .total(orderPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public com.memento3d.orders.api.response.OrderResponse getOrderDetail(UUID orderId, UUID userId, com.memento3d.identity.domain.model.Role userRole) {
        Order order = getOrderById(orderId);
        boolean isStaff = (userRole == com.memento3d.identity.domain.model.Role.ROLE_ADMIN 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_MANAGER 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_OPERATOR);
        if (!isStaff && !order.getUserId().equals(userId)) {
            log.warn("Access denied: User {} cannot view order {}", userId, orderId);
            throw new AccessDeniedException("User is not authorized to view this order");
        }
        return com.memento3d.orders.api.response.OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public com.memento3d.orders.api.response.OrderResponse updateOrder(UUID orderId, com.memento3d.orders.api.request.UpdateOrderRequest request, UUID userId, com.memento3d.identity.domain.model.Role userRole) {
        Order order = getOrderById(orderId);
        boolean isStaff = (userRole == com.memento3d.identity.domain.model.Role.ROLE_ADMIN 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_MANAGER 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_OPERATOR);
        if (!isStaff && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("User is not authorized to update this order");
        }

        if (request.getRecipientName() != null && !request.getRecipientName().isBlank()) {
            order.setRecipientName(request.getRecipientName());
        }
        if (request.getShippingAddress() != null && !request.getShippingAddress().isBlank()) {
            order.setShippingAddress(request.getShippingAddress());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            order.setPhone(request.getPhone());
        }

        if (request.getStatus() != null) {
            if (!isStaff) {
                throw new AccessDeniedException("Only staff (Admin / Manager / Operator) can update order status directly");
            }
            order.setStatus(request.getStatus());
        }

        Order updated = orderRepository.save(order);
        log.info("Order {} updated successfully", orderId);
        return com.memento3d.orders.api.response.OrderResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void cancelOrder(UUID orderId, UUID userId, com.memento3d.identity.domain.model.Role userRole) {
        Order order = getOrderById(orderId);
        boolean isStaff = (userRole == com.memento3d.identity.domain.model.Role.ROLE_ADMIN 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_MANAGER 
                || userRole == com.memento3d.identity.domain.model.Role.ROLE_OPERATOR);
        if (!isStaff && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("User is not authorized to cancel this order");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && !isStaff) {
            throw new IllegalStateException("Cannot cancel an order that is already confirmed or processing. Please contact support.");
        }

        order.cancel();
        orderRepository.save(order);
        log.info("Order {} cancelled by user {} (role: {})", orderId, userId, userRole);
    }
}

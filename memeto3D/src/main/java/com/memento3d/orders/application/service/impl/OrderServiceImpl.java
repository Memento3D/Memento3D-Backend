package com.memento3d.orders.application.service.impl;

import com.memento3d.cart.domain.exception.CartEmptyException;
import com.memento3d.cart.domain.model.Cart;
import com.memento3d.cart.domain.model.CartItem;
import com.memento3d.cart.infrastructure.persistence.repository.CartItemRepository;
import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.api.request.CreateOrderRequest;
import com.memento3d.orders.api.request.UpdateOrderRequest;
import com.memento3d.orders.api.response.OrderListResponse;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.exception.InvalidOrderStatusException;
import com.memento3d.orders.domain.exception.OrderAccessDeniedException;
import com.memento3d.orders.domain.exception.OrderNotFoundException;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderItemRepository;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.exception.PaymentAlreadyPaidException;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.memento3d.products.domain.exception.ProductInactiveException;
import com.memento3d.products.domain.exception.ProductNotFoundException;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final com.memento3d.pricing.application.service.PricingEngineService pricingEngineService;
    private final com.memento3d.entitlement.application.service.EntitlementService entitlementService;
    private final com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;

    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public Order validateOrderForPayment(UUID orderId, UUID userId) {
        Order order = getOrderById(orderId);
        if (!order.getUserId().equals(userId)) {
            log.warn("Access denied: User {} does not own order {}", userId, orderId);
            throw new OrderAccessDeniedException("User is not authorized to access this order");
        }
        if (!order.isEligibleForPayment()) {
            log.warn("Order {} is not eligible for payment. Current status: {}, paymentStatus: {}",
                    orderId, order.getStatus(), order.getPaymentStatus());
            throw new PaymentAlreadyPaidException("Order is not eligible for payment. Status: " + order.getStatus() + ", paymentStatus: " + order.getPaymentStatus());
        }
        return order;
    }

    @Override
    @Transactional
    public void updateOrderStatusPaid(UUID orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));
        if (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.info("Order {} is already paid", orderId);
            return;
        }
        order.markAsPaid();
        orderRepository.save(order);
        log.info("Order {} payment confirmed, status updated to {}", orderId, order.getStatus());

        // Automatically grant entitlements on successful payment
        UUID scanId = order.getScanId();
        if (scanId != null) {
            entitlementService.grantEntitlement(
                    order.getUserId(),
                    null,
                    scanId,
                    "SCAN",
                    com.memento3d.entitlement.domain.model.EntitlementType.VIEW_HIGH_QUALITY,
                    null
            );
            entitlementService.grantEntitlement(
                    order.getUserId(),
                    null,
                    scanId,
                    "SCAN",
                    com.memento3d.entitlement.domain.model.EntitlementType.DOWNLOAD_DIGITAL_MODEL,
                    null
            );
        }

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                UUID resourceId = scanId != null ? scanId : item.getId();
                entitlementService.grantEntitlement(
                        order.getUserId(),
                        item.getId(),
                        resourceId,
                        "ORDER_ITEM",
                        com.memento3d.entitlement.domain.model.EntitlementType.VIEW_HIGH_QUALITY,
                        null
                );
                entitlementService.grantEntitlement(
                        order.getUserId(),
                        item.getId(),
                        resourceId,
                        "ORDER_ITEM",
                        com.memento3d.entitlement.domain.model.EntitlementType.DOWNLOAD_DIGITAL_MODEL,
                        null
                );
            }
        }

        // Publish Outbox Event for Notification & Analytics
        outboxPublisher.publish(
                "ORDER",
                order.getId().toString(),
                "PAYMENT_SUCCEEDED",
                java.util.Map.of(
                        "orderId", order.getId(),
                        "userId", order.getUserId(),
                        "totalPrice", order.getTotalPrice(),
                        "paymentMethod", order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "UNKNOWN",
                        "fulfillmentType", order.getFulfillmentType() != null ? order.getFulfillmentType() : "PHYSICAL"
                )
        );
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, UUID userId) {
        log.info("Creating order for user ID={}", userId);

        // Idempotency check: if request has idempotencyKey and order already exists, return it
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingOrder.isPresent()) {
                log.info("Returning existing order for idempotencyKey: {}", request.getIdempotencyKey());
                return OrderResponse.fromEntity(existingOrder.get());
            }
        }

        // Quote validation: if quoteId is provided, verify and lock it with server-side pricing engine
        if (request.getQuoteId() != null) {
            pricingEngineService.validateAndLockQuote(request.getQuoteId(), request.getTotalPrice(), userId);
        }

        // 1. Check if user has an active cart with eager loaded items & products
        Optional<Cart> cartOpt = cartRepository.findWithItemsByCustomerId(userId);
        Cart cart = cartOpt.orElse(null);

        boolean hasCartItems = cart != null && cart.getItems() != null && !cart.getItems().isEmpty();

        Order order;
        String orderCode = generateOrderCode();

        if (hasCartItems) {
            // E-commerce Cart Checkout flow
            List<OrderItem> orderItems = new ArrayList<>();
            BigDecimal subtotal = BigDecimal.ZERO;

            for (CartItem cartItem : cart.getItems()) {
                Product product = cartItem.getProduct();
                if (product == null) {
                    product = productRepository.findById(cartItem.getProduct().getId())
                            .orElseThrow(() -> new ProductNotFoundException("Product not found: " + cartItem.getProduct().getId()));
                }

                if (!product.isPurchasable()) {
                    throw new ProductInactiveException("Product '" + product.getName() + "' is no longer active or in stock");
                }

                BigDecimal itemUnitPrice = product.getPrice();
                int qty = cartItem.getQuantity();
                BigDecimal itemSubtotal = itemUnitPrice.multiply(BigDecimal.valueOf(qty));
                subtotal = subtotal.add(itemSubtotal);

                OrderItem orderItem = OrderItem.builder()
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .unitPrice(itemUnitPrice)
                        .quantity(qty)
                        .subtotal(itemSubtotal)
                        .customizationSnapshot(cartItem.getCustomizationData())
                        .build();

                orderItems.add(orderItem);
            }

            BigDecimal discountAmount = BigDecimal.ZERO;
            BigDecimal shippingFee = BigDecimal.ZERO;
            BigDecimal totalAmount = subtotal.subtract(discountAmount).add(shippingFee);

            order = Order.builder()
                    .orderCode(orderCode)
                    .userId(userId)
                    .subtotal(subtotal)
                    .discountAmount(discountAmount)
                    .shippingFee(shippingFee)
                    .totalPrice(totalAmount)
                    .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                    .status(OrderStatus.PENDING)
                    .fulfillmentType(request.getFulfillmentType() != null ? request.getFulfillmentType() : "PHYSICAL")
                    .paymentType(request.getPaymentType() != null ? request.getPaymentType() : "FULL_PAYMENT")
                    .idempotencyKey(request.getIdempotencyKey())
                    .paymentMethod(request.getPaymentMethod())
                    .paymentStatus(PaymentStatus.PENDING)
                    .recipientName(request.getRecipientName())
                    .shippingAddress(request.getShippingAddress())
                    .phone(request.getPhone())
                    .note(request.getNote())
                    .items(new ArrayList<>())
                    .build();

            for (OrderItem oi : orderItems) {
                order.addItem(oi);
            }

            order = orderRepository.save(order);

            // Clear customer cart
            cart.clearItems();
            cartRepository.save(cart);
            log.info("Cart cleared for user ID={}", userId);

        } else if (request.getScanId() != null && request.getTotalPrice() != null) {
            // Legacy Bespoke Scan 3D Order
            order = Order.builder()
                    .orderCode(orderCode)
                    .userId(userId)
                    .scanId(request.getScanId())
                    .subtotal(request.getTotalPrice())
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalPrice(request.getTotalPrice())
                    .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                    .status(OrderStatus.PENDING)
                    .fulfillmentType(request.getFulfillmentType() != null ? request.getFulfillmentType() : "PHYSICAL")
                    .paymentType(request.getPaymentType() != null ? request.getPaymentType() : "FULL_PAYMENT")
                    .idempotencyKey(request.getIdempotencyKey())
                    .paymentMethod(request.getPaymentMethod())
                    .paymentStatus(PaymentStatus.PENDING)
                    .recipientName(request.getRecipientName())
                    .shippingAddress(request.getShippingAddress())
                    .phone(request.getPhone())
                    .note(request.getNote())
                    .items(new ArrayList<>())
                    .build();

            order = orderRepository.save(order);
        } else {
            throw new CartEmptyException("Shopping cart is empty. Please add items to your cart before checking out.");
        }

        // If customer pre-selected CASH, create the pending CASH payment record immediately
        if (order.getPaymentMethod() == PaymentMethod.CASH) {
            String merchantTxId = "TX-CASH-" + System.currentTimeMillis() + "-" + (1000 + random.nextInt(9000));
            PaymentTransaction cashTx = PaymentTransaction.builder()
                    .orderId(order.getId())
                    .userId(userId)
                    .paymentMethod(PaymentMethod.CASH)
                    .provider("CASH")
                    .amount(order.getTotalPrice())
                    .currency(order.getCurrency())
                    .status(PaymentStatus.PENDING)
                    .merchantTransactionId(merchantTxId)
                    .requestId(UUID.randomUUID().toString())
                    .build();
            paymentTransactionRepository.save(cashTx);
            log.info("Created PENDING Cash transaction ID={} for order ID={}", cashTx.getId(), order.getId());
        }

        log.info("Order created successfully: ID={}, code={}, total={}", order.getId(), order.getOrderCode(), order.getTotalPrice());
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderListResponse getUserOrders(UUID userId, Role userRole, int page, int limit) {
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = limit > 0 ? Math.min(limit, 100) : 20;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> orderPage;
        boolean isStaff = (userRole == Role.ROLE_ADMIN 
                || userRole == Role.ROLE_MANAGER 
                || userRole == Role.ROLE_OPERATOR);
        if (isStaff) {
            orderPage = orderRepository.findAll(pageable);
        } else {
            orderPage = orderRepository.findByUserId(userId, pageable);
        }

        return OrderListResponse.builder()
                .orders(orderPage.getContent().stream().map(OrderResponse::fromEntity).toList())
                .page(page)
                .limit(pageSize)
                .total(orderPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderListResponse getMyOrders(UUID userId, OrderStatus status, int page, int limit) {
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = limit > 0 ? Math.min(limit, 100) : 20;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> orderPage;
        if (status != null) {
            orderPage = orderRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            orderPage = orderRepository.findByUserId(userId, pageable);
        }

        return OrderListResponse.builder()
                .orders(orderPage.getContent().stream().map(OrderResponse::fromEntity).toList())
                .page(page)
                .limit(pageSize)
                .total(orderPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderListResponse getManagerOrders(String query, OrderStatus status, int page, int limit) {
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = limit > 0 ? Math.min(limit, 100) : 20;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String trimmedQuery = query != null && !query.isBlank() ? query.trim() : null;
        Page<Order> orderPage = orderRepository.searchManagerOrders(trimmedQuery, status, pageable);

        return OrderListResponse.builder()
                .orders(orderPage.getContent().stream().map(OrderResponse::fromEntity).toList())
                .page(page)
                .limit(pageSize)
                .total(orderPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus nextStatus, UUID managerId, Role role) {
        Order order = getOrderById(orderId);
        boolean isStaff = (role == Role.ROLE_ADMIN || role == Role.ROLE_MANAGER || role == Role.ROLE_OPERATOR);
        if (!isStaff) {
            throw new OrderAccessDeniedException("Only Managers and Admins can update order status");
        }

        if (!order.canTransitionTo(nextStatus)) {
            throw new InvalidOrderStatusException("Cannot transition order from " + order.getStatus() + " to " + nextStatus);
        }

        order.setStatus(nextStatus);
        Order saved = orderRepository.save(order);
        log.info("Manager ID={} updated order ID={} status to {}", managerId, orderId, nextStatus);

        outboxPublisher.publish(
                "ORDER",
                saved.getId().toString(),
                "ORDER_STATUS_CHANGED",
                java.util.Map.of(
                        "orderId", saved.getId(),
                        "userId", saved.getUserId(),
                        "status", saved.getStatus().name(),
                        "paymentStatus", saved.getPaymentStatus() != null ? saved.getPaymentStatus().name() : "PENDING"
                )
        );

        return OrderResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(UUID orderId, UUID userId, Role userRole) {
        Order order = orderRepository.findWithItemsById(orderId)
                .or(() -> orderRepository.findById(orderId))
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));
        boolean isStaff = (userRole == Role.ROLE_ADMIN 
                || userRole == Role.ROLE_MANAGER 
                || userRole == Role.ROLE_OPERATOR);
        if (!isStaff && !order.getUserId().equals(userId)) {
            log.warn("Access denied: User {} cannot view order {}", userId, orderId);
            throw new OrderAccessDeniedException("You are not authorized to view this order");
        }
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(UUID orderId, UpdateOrderRequest request, UUID userId, Role userRole) {
        Order order = getOrderById(orderId);
        boolean isStaff = (userRole == Role.ROLE_ADMIN 
                || userRole == Role.ROLE_MANAGER 
                || userRole == Role.ROLE_OPERATOR);
        if (!isStaff && !order.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("You are not authorized to update this order");
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
                throw new OrderAccessDeniedException("Only staff (Admin / Manager) can update order status directly");
            }
            if (!order.canTransitionTo(request.getStatus())) {
                throw new InvalidOrderStatusException("Cannot transition order from " + order.getStatus() + " to " + request.getStatus());
            }
            order.setStatus(request.getStatus());
        }

        Order updated = orderRepository.save(order);
        log.info("Order {} updated successfully", orderId);

        outboxPublisher.publish(
                "ORDER",
                updated.getId().toString(),
                "ORDER_STATUS_CHANGED",
                java.util.Map.of(
                        "orderId", updated.getId(),
                        "userId", updated.getUserId(),
                        "status", updated.getStatus().name(),
                        "paymentStatus", updated.getPaymentStatus() != null ? updated.getPaymentStatus().name() : "PENDING"
                )
        );

        return OrderResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void cancelOrder(UUID orderId, UUID userId, Role userRole) {
        Order order = getOrderById(orderId);
        boolean isStaff = (userRole == Role.ROLE_ADMIN 
                || userRole == Role.ROLE_MANAGER 
                || userRole == Role.ROLE_OPERATOR);
        if (!isStaff && !order.getUserId().equals(userId)) {
            throw new OrderAccessDeniedException("You are not authorized to cancel this order");
        }

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderStatusException("Cannot cancel an order that has already been delivered or completed.");
        }

        order.cancel();
        Order saved = orderRepository.save(order);
        log.info("Order {} cancelled by user {} (role: {})", orderId, userId, userRole);

        outboxPublisher.publish(
                "ORDER",
                saved.getId().toString(),
                "ORDER_STATUS_CHANGED",
                java.util.Map.of(
                        "orderId", saved.getId(),
                        "userId", saved.getUserId(),
                        "status", saved.getStatus().name(),
                        "paymentStatus", saved.getPaymentStatus() != null ? saved.getPaymentStatus().name() : "CANCELLED"
                )
        );
    }

    private String generateOrderCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randPart = String.format("%04d", random.nextInt(10000));
        return "ORD-" + datePart + "-" + randPart;
    }
}

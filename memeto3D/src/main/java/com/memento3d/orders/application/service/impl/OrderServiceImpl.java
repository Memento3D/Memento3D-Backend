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
import com.memento3d.orders.domain.event.OrderConfirmedEvent;
import com.memento3d.orders.domain.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
    private final com.memento3d.operations.reviewqueue.application.service.ManualReviewService manualReviewService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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

        // Publish Order Confirmed Event for Email Notification
        applicationEventPublisher.publishEvent(new OrderConfirmedEvent(order.getId()));
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
        BigDecimal serverVerifiedPrice = null;
        if (request.getQuoteId() != null) {
            com.memento3d.pricing.domain.model.PriceQuote lockedQuote =
                    pricingEngineService.validateAndLockQuote(request.getQuoteId(), request.getTotalPrice(), userId);
            serverVerifiedPrice = lockedQuote.getTotalAmount();
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

                Map<String, Object> custMap = null;
                if (cartItem.getCustomizationData() != null && !cartItem.getCustomizationData().isBlank()) {
                    try {
                        custMap = OBJECT_MAPPER.readValue(cartItem.getCustomizationData(), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception ignored) {}
                }

                String materialSnap = custMap != null && custMap.get("material") != null ? custMap.get("material").toString() : "Chưa xác định";
                String sizeSnap = custMap != null && custMap.get("size") != null ? custMap.get("size").toString() : "Chưa xác định";
                String baseSnap = custMap != null && custMap.get("base") != null ? custMap.get("base").toString() : "Tiêu chuẩn";
                String finishSnap = custMap != null && custMap.get("finish") != null ? custMap.get("finish").toString() : "Phủ bóng UV bảo vệ";
                String skuSnap = product.getSlug() != null ? "SKU-" + product.getSlug().toUpperCase() : "SKU-" + product.getId().toString().substring(0, 8);
                String catSnap = product.getCategory() != null ? product.getCategory().getName() : "Kỷ vật 3D";
                String techSnap = materialSnap.toLowerCase().contains("resin") ? "SLA (Stereolithography)" : "FDM (Fused Deposition Modeling)";

                OrderItem orderItem = OrderItem.builder()
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .unitPrice(itemUnitPrice)
                        .quantity(qty)
                        .subtotal(itemSubtotal)
                        .customizationSnapshot(cartItem.getCustomizationData())
                        .skuSnapshot(skuSnap)
                        .categoryNameSnapshot(catSnap)
                        .materialSnapshot(materialSnap)
                        .dimensionsSnapshot(sizeSnap)
                        .finishSnapshot(finishSnap)
                        .baseSnapshot(baseSnap)
                        .technologySnapshot(techSnap)
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

        } else if (request.getScanId() != null) {
            // Bespoke Scan 3D Order: Price must be server-authoritative
            BigDecimal finalBespokePrice = serverVerifiedPrice;
            if (finalBespokePrice == null) {
                if (request.getTotalPrice() == null || request.getTotalPrice().compareTo(BigDecimal.valueOf(50000)) < 0) {
                    throw new com.memento3d.shared.exception.BusinessException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "PRICE_TAMPERING_DETECTED",
                            "Giá trị đơn hàng chế tác 3D không hợp lệ hoặc bị can thiệp. Vui lòng nhận báo giá chính xác từ hệ thống.");
                }
                finalBespokePrice = request.getTotalPrice();
            }

            order = Order.builder()
                    .orderCode(orderCode)
                    .userId(userId)
                    .scanId(request.getScanId())
                    .subtotal(finalBespokePrice)
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalPrice(finalBespokePrice)
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

            OrderItem scanItem = OrderItem.builder()
                    .order(order)
                    .productNameSnapshot("Kỷ vật 3D chân dung (Scan AI)")
                    .unitPrice(finalBespokePrice)
                    .quantity(1)
                    .subtotal(finalBespokePrice)
                    .skuSnapshot("BESPOKE-SCAN-3D")
                    .categoryNameSnapshot("Tượng Chân Dung 3D")
                    .materialSnapshot("High-Detail Resin SLA")
                    .technologySnapshot("SLA (Stereolithography)")
                    .dimensionsSnapshot("Tỷ lệ 1:10 (15-18cm)")
                    .finishSnapshot("Xử lý bề mặt thủ công & Phủ UV bảo vệ")
                    .baseSnapshot("Đế gỗ óc chó tự nhiên chạm tên")
                    .customizationSnapshot("{\"material\": \"High-Detail Resin SLA\", \"technology\": \"SLA\", \"size\": \"15-18cm\", \"base\": \"Đế gỗ óc chó tự nhiên\"}")
                    .build();
            order.addItem(scanItem);

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
    public OrderListResponse getUserOrders(UUID userId, Role userRole, String query, OrderStatus status, int page, int limit) {
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = limit > 0 ? Math.min(limit, 100) : 20;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        boolean isStaff = (userRole == Role.ROLE_ADMIN 
                || userRole == Role.ROLE_MANAGER 
                || userRole == Role.ROLE_OPERATOR);
        UUID targetUserId = isStaff ? null : userId;
        String trimmedQuery = query != null && !query.isBlank() ? query.trim() : null;

        Page<Order> orderPage = orderRepository.searchUserOrders(targetUserId, trimmedQuery, status, pageable);

        // Batch initialize items for current page (avoids N+1 query via @BatchSize(50))
        for (Order o : orderPage.getContent()) {
            if (o.getItems() != null) {
                o.getItems().size();
            }
        }

        return OrderListResponse.builder()
                .orders(orderPage.getContent().stream().map(o -> OrderResponse.fromEntity(o, isStaff)).toList())
                .page(page)
                .limit(pageSize)
                .total(orderPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderListResponse getMyOrders(UUID userId, String query, OrderStatus status, int page, int limit) {
        return getUserOrders(userId, Role.ROLE_USER, query, status, page, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderListResponse getManagerOrders(String query, OrderStatus status, int page, int limit) {
        int pageIndex = Math.max(page - 1, 0);
        int pageSize = limit > 0 ? Math.min(limit, 100) : 20;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String trimmedQuery = query != null && !query.isBlank() ? query.trim() : null;
        Page<Order> orderPage = orderRepository.searchManagerOrders(trimmedQuery, status, pageable);

        // Batch initialize items for current page (avoids N+1 query via @BatchSize(50))
        for (Order o : orderPage.getContent()) {
            if (o.getItems() != null) {
                o.getItems().size();
            }
        }

        return OrderListResponse.builder()
                .orders(orderPage.getContent().stream().map(o -> OrderResponse.fromEntity(o, true)).toList())
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
                        "userId", saved.getUserId() != null ? saved.getUserId() : (order.getUserId() != null ? order.getUserId() : UUID.fromString("00000000-0000-0000-0000-000000000000")),
                        "status", saved.getStatus().name(),
                        "paymentStatus", saved.getPaymentStatus() != null ? saved.getPaymentStatus().name() : "PENDING"
                )
        );

        if (nextStatus == OrderStatus.CONFIRMED) {
            applicationEventPublisher.publishEvent(new OrderConfirmedEvent(saved.getId()));
        } else if (nextStatus == OrderStatus.DELIVERED || nextStatus == OrderStatus.COMPLETED) {
            applicationEventPublisher.publishEvent(new OrderCompletedEvent(saved.getId()));
        }

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
        return OrderResponse.fromEntity(order, isStaff);
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
                        "userId", updated.getUserId() != null ? updated.getUserId() : (order.getUserId() != null ? order.getUserId() : UUID.fromString("00000000-0000-0000-0000-000000000000")),
                        "status", updated.getStatus().name(),
                        "paymentStatus", updated.getPaymentStatus() != null ? updated.getPaymentStatus().name() : "PENDING"
                )
        );

        if (request.getStatus() == OrderStatus.CONFIRMED) {
            applicationEventPublisher.publishEvent(new OrderConfirmedEvent(updated.getId()));
        } else if (request.getStatus() == OrderStatus.DELIVERED || request.getStatus() == OrderStatus.COMPLETED) {
            applicationEventPublisher.publishEvent(new OrderCompletedEvent(updated.getId()));
        }

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

        if (!isStaff && (order.getStatus() == OrderStatus.PRINTING || order.getStatus() == OrderStatus.READY)) {
            // Customer requested cancellation after production started: enqueue exception for manual review
            manualReviewService.enqueueReview(
                    com.memento3d.operations.reviewqueue.domain.model.ReviewType.REFUND,
                    com.memento3d.operations.reviewqueue.domain.model.ReviewPriority.HIGH,
                    order.getId(),
                    order.getOrderCode(),
                    userId,
                    BigDecimal.valueOf(0.50),
                    "CUSTOMER_CANCELLED_DURING_PRODUCTION",
                    "Khách hàng yêu cầu hủy đơn khi máy in đã bắt đầu sản xuất. Quản lý cần kiểm tra hao phí vật tư để quyết toán.",
                    "CALCULATE_SCRAP_AND_PARTIAL_REFUND"
            );
            throw new com.memento3d.shared.exception.BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "ORDER_IN_PRODUCTION",
                    "Đơn hàng đang trong quá trình chế tác in 3D. Yêu cầu hủy đã được chuyển tới Quản lý để xem xét.");
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

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getOrderStatusCounts(UUID userId, Role role) {
        boolean isStaff = (role == Role.ROLE_ADMIN || role == Role.ROLE_MANAGER || role == Role.ROLE_OPERATOR);
        List<Object[]> rows = isStaff
                ? orderRepository.countOrdersGroupedByStatus()
                : orderRepository.countOrdersGroupedByStatusForUser(userId);

        Map<String, Long> counts = new HashMap<>();
        long total = 0;
        if (rows != null) {
            for (Object[] r : rows) {
                if (r != null && r.length >= 2 && r[0] != null) {
                    String st = r[0].toString();
                    long c = ((Number) r[1]).longValue();
                    counts.put(st, c);
                    total += c;
                }
            }
        }
        counts.put("ALL", total);
        return counts;
    }

    @Override
    @Transactional
    public OrderResponse reviewFeasibility(UUID orderId, com.memento3d.orders.api.request.OrderFeasibilityReviewRequest request, UUID managerId, Role role) {
        boolean isStaff = (role == Role.ROLE_ADMIN || role == Role.ROLE_MANAGER || role == Role.ROLE_OPERATOR);
        if (!isStaff) {
            throw new OrderAccessDeniedException("Chỉ Quản lý hoặc Kỹ thuật viên xưởng mới có quyền kiểm tra khả năng sản xuất");
        }

        Order order = getOrderById(orderId);

        String decision = request.getEffectiveDecision() != null ? request.getEffectiveDecision().trim().toUpperCase() : "FEASIBLE";
        BigDecimal finalPrice = request.getEffectiveFinalPrice();

        // Business rule: Do not modify quotes on already paid orders
        if ((order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.SUCCESS)
                && finalPrice != null
                && finalPrice.compareTo(order.getTotalPrice()) != 0) {
            throw new com.memento3d.shared.exception.BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "PAID_ORDER_PRICE_IMMUTABLE",
                    "Không thể chỉnh sửa báo giá của đơn hàng đã hoàn tất thanh toán");
        }

        order.setReviewedBy(managerId);
        order.setReviewedAt(Instant.now());
        order.setFeasibilityNotes(request.getNotes());

        if (request.getEstimatedDays() != null) {
            order.setEstimatedProductionDays(request.getEstimatedDays());
        }

        if (request.getRevisionGlbUrl() != null && !request.getRevisionGlbUrl().isBlank()) {
            order.setRevisionGlbUrl(request.getRevisionGlbUrl().trim());
        }

        if ("FEASIBLE".equals(decision)) {
            order.setFeasibilityStatus("FEASIBLE");
            if (finalPrice != null && finalPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
                BigDecimal shipping = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;
                order.setSubtotal(finalPrice);
                order.setTotalPrice(finalPrice.subtract(discount).add(shipping));
            }
            log.info("Order ID={} approved as FEASIBLE by manager ID={}", orderId, managerId);
        } else if ("REVISION_REQUESTED".equals(decision)) {
            order.setFeasibilityStatus("REVISION_REQUESTED");
            log.info("Order ID={} marked as REVISION_REQUESTED by manager ID={}", orderId, managerId);
        } else if ("REJECTED".equals(decision)) {
            order.setFeasibilityStatus("REJECTED");
            order.setStatus(OrderStatus.CANCELLED);
            log.info("Order ID={} marked as REJECTED (CANCELLED) by manager ID={}", orderId, managerId);
        } else {
            throw new IllegalArgumentException("Quyết định kiểm tra không hợp lệ: " + decision);
        }

        Order saved = orderRepository.save(order);

        // Outbox notification to customer
        outboxPublisher.publish(
                "ORDER",
                saved.getId().toString(),
                "ORDER_FEASIBILITY_REVIEWED",
                java.util.Map.of(
                        "orderId", saved.getId(),
                        "userId", saved.getUserId(),
                        "decision", decision,
                        "notes", order.getFeasibilityNotes() != null ? order.getFeasibilityNotes() : "",
                        "totalPrice", saved.getTotalPrice()
                )
        );

        return OrderResponse.fromEntity(saved, true);
    }

    @Override
    @Transactional
    public OrderResponse acceptWorkshopQuotation(UUID orderId, UUID customerId) {
        Order order = getOrderById(orderId);
        if (!order.getUserId().equals(customerId)) {
            throw new OrderAccessDeniedException("Bạn không có quyền thao tác trên đơn hàng của người dùng khác");
        }

        if (!"FEASIBLE".equalsIgnoreCase(order.getFeasibilityStatus()) && !"REVISION_REQUESTED".equalsIgnoreCase(order.getFeasibilityStatus())) {
            throw new com.memento3d.shared.exception.BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "ORDER_NOT_AWAITING_APPROVAL",
                    "Đơn hàng hiện không ở trạng thái chờ khách hàng duyệt báo giá hoặc mẫu chỉnh sửa");
        }

        order.setCustomerAcceptedAt(Instant.now());
        order.setFeasibilityStatus("ACCEPTED");
        Order saved = orderRepository.save(order);
        log.info("Customer ID={} accepted workshop quotation/revision for order ID={}", customerId, orderId);

        outboxPublisher.publish(
                "ORDER",
                saved.getId().toString(),
                "CUSTOMER_ACCEPTED_QUOTATION",
                java.util.Map.of(
                        "orderId", saved.getId(),
                        "userId", saved.getUserId(),
                        "totalPrice", saved.getTotalPrice()
                )
        );

        return OrderResponse.fromEntity(saved, false);
    }

    private String generateOrderCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randPart = String.format("%04d", random.nextInt(10000));
        return "ORD-" + datePart + "-" + randPart;
    }
}

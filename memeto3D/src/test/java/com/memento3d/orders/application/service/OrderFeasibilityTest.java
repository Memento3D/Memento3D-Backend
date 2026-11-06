package com.memento3d.orders.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.api.request.OrderFeasibilityReviewRequest;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.application.service.impl.OrderServiceImpl;
import com.memento3d.orders.domain.exception.OrderAccessDeniedException;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFeasibilityTest {
    @Mock private com.memento3d.customization.application.service.PrintDesignService printDesignService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxPublisherService outboxPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID orderId;
    private UUID customerId;
    private UUID managerId;
    private Order scanOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        managerId = UUID.randomUUID();

        scanOrder = Order.builder()
                .id(orderId)
                .orderCode("MM3D-TEST")
                .userId(customerId)
                .scanId(UUID.randomUUID())
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.PENDING)
                .feasibilityStatus("PENDING_REVIEW")
                .totalPrice(new BigDecimal("350000"))
                .currency("VND")
                .recipientName("Nguyen Van A")
                .phone("0901234567")
                .shippingAddress("123 Le Loi, TP.HCM")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should block payment when bespoke 3D scan order is PENDING_REVIEW")
    void testIsEligibleForPayment_PendingFeasibility() {
        assertFalse(scanOrder.isEligibleForPayment(), "Pending review scan orders must not be eligible for payment");

        scanOrder.setFeasibilityStatus("FEASIBLE");
        assertTrue(scanOrder.isEligibleForPayment(), "Feasible scan orders must be eligible for payment");

        scanOrder.setFeasibilityStatus("ACCEPTED");
        assertTrue(scanOrder.isEligibleForPayment(), "Accepted scan orders must be eligible for payment");
    }

    @Test
    @DisplayName("Workshop reviewFeasibility with FEASIBLE should update price, lead time and publish outbox event")
    void testReviewFeasibility_Success() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(scanOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderFeasibilityReviewRequest request = OrderFeasibilityReviewRequest.builder()
                .decision("FEASIBLE")
                .notes("Mô hình kín, đủ điều kiện in SLA")
                .finalPrice(new BigDecimal("420000"))
                .estimatedDays(4)
                .build();

        OrderResponse response = orderService.reviewFeasibility(orderId, request, managerId, Role.ROLE_MANAGER);

        assertNotNull(response);
        assertEquals("FEASIBLE", scanOrder.getFeasibilityStatus());
        assertEquals("Mô hình kín, đủ điều kiện in SLA", scanOrder.getFeasibilityNotes());
        assertEquals(new BigDecimal("420000"), scanOrder.getTotalPrice());
        assertEquals(4, scanOrder.getEstimatedProductionDays());
        assertEquals(managerId, scanOrder.getReviewedBy());
        assertNotNull(scanOrder.getReviewedAt());

        verify(outboxPublisher).publish(eq("ORDER"), eq(orderId.toString()), eq("ORDER_FEASIBILITY_REVIEWED"), any());
    }

    @Test
    @DisplayName("Workshop reviewFeasibility should block price tampering if order is already PAID")
    void testReviewFeasibility_BlockPaidPriceTampering() {
        scanOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(scanOrder));

        OrderFeasibilityReviewRequest request = OrderFeasibilityReviewRequest.builder()
                .decision("FEASIBLE")
                .notes("Thay đổi giá sau khi thanh toán")
                .finalPrice(new BigDecimal("500000"))
                .build();

        assertThrows(com.memento3d.shared.exception.BusinessException.class, () ->
                orderService.reviewFeasibility(orderId, request, managerId, Role.ROLE_MANAGER)
        );

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Customer acceptWorkshopQuotation should transition to ACCEPTED when invoked by order owner")
    void testAcceptWorkshopQuotation_Success() {
        scanOrder.setFeasibilityStatus("FEASIBLE");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(scanOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.acceptWorkshopQuotation(orderId, customerId);

        assertNotNull(response);
        assertEquals("ACCEPTED", scanOrder.getFeasibilityStatus());
        assertNotNull(scanOrder.getCustomerAcceptedAt());

        verify(outboxPublisher).publish(eq("ORDER"), eq(orderId.toString()), eq("CUSTOMER_ACCEPTED_QUOTATION"), any());
    }

    @Test
    @DisplayName("Customer acceptWorkshopQuotation should throw OrderAccessDeniedException if called by another user")
    void testAcceptWorkshopQuotation_AccessDenied() {
        UUID anotherUser = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(scanOrder));

        assertThrows(OrderAccessDeniedException.class, () ->
                orderService.acceptWorkshopQuotation(orderId, anotherUser)
        );

        verify(orderRepository, never()).save(any());
    }
}

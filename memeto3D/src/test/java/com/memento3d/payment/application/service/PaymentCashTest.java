package com.memento3d.payment.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.payment.api.request.CreatePaymentRequest;
import com.memento3d.payment.api.response.PaymentResponse;
import com.memento3d.payment.api.response.PaymentStatusResponse;
import com.memento3d.payment.application.service.impl.PaymentServiceImpl;
import com.memento3d.payment.domain.gateway.PaymentGatewayFactory;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCashTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentGatewayFactory gatewayFactory;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID customerId;
    private UUID managerId;
    private UUID orderId;
    private Order order;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        order = Order.builder()
                .id(orderId)
                .userId(customerId)
                .totalPrice(new BigDecimal("500000"))
                .currency("VND")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("CASH payment creation must not invoke external payment gateway")
    void testCashPaymentCreation() {
        when(orderService.validateOrderForPayment(orderId, customerId)).thenReturn(order);
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.PAID)).thenReturn(Optional.empty());
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> {
            PaymentTransaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.CASH)
                .build();

        PaymentResponse response = paymentService.createPayment(request, customerId);

        assertNotNull(response);
        assertEquals(PaymentMethod.CASH, response.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertNull(response.getPaymentUrl());

        // Verify gatewayFactory was never called!
        verifyNoInteractions(gatewayFactory);
    }

    @Test
    @DisplayName("Customer cannot confirm CASH payment directly")
    void testCustomerCannotConfirmCash() {
        UUID paymentId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () ->
                paymentService.confirmCashPayment(paymentId, customerId, Role.ROLE_USER));

        verify(transactionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Manager can confirm CASH payment, updating transaction and order to PAID")
    void testManagerCanConfirmCash() {
        UUID paymentId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(paymentId)
                .orderId(orderId)
                .userId(customerId)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("500000"))
                .status(PaymentStatus.PENDING)
                .merchantTransactionId("TX-CASH-1234")
                .build();

        when(transactionRepository.findById(paymentId)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentStatusResponse response = paymentService.confirmCashPayment(paymentId, managerId, Role.ROLE_MANAGER);

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.getStatus());

        // Verify order status updated
        verify(orderService).updateOrderStatusPaid(orderId);
    }
}

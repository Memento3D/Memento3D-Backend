package com.memento3d.payment.api;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.payment.api.request.CreatePaymentRequest;
import com.memento3d.payment.api.response.PaymentResponse;
import com.memento3d.payment.api.response.PaymentStatusResponse;
import com.memento3d.payment.application.service.PaymentService;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private CustomUserDetails mockUserDetails;
    private UUID userId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .password("password")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build();
        mockUserDetails = new CustomUserDetails(user);
    }

    @Test
    @DisplayName("Create payment endpoint returns 201 Created and PaymentResponse")
    void testCreatePaymentEndpoint() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        PaymentResponse mockResponse = PaymentResponse.builder()
                .paymentId(UUID.randomUUID())
                .orderId(orderId)
                .paymentMethod(PaymentMethod.MOMO)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("50000.00"))
                .currency("VND")
                .paymentUrl("https://test-payment.momo.vn/pay/123")
                .build();

        when(paymentService.createPayment(eq(request), eq(userId))).thenReturn(mockResponse);

        ResponseEntity<PaymentResponse> response = paymentController.createPayment(request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("https://test-payment.momo.vn/pay/123", response.getBody().getPaymentUrl());
        verify(paymentService).createPayment(request, userId);
    }

    @Test
    @DisplayName("Get payment status endpoint returns 200 OK and PaymentStatusResponse")
    void testGetPaymentStatusEndpoint() {
        UUID paymentId = UUID.randomUUID();
        PaymentStatusResponse mockResponse = PaymentStatusResponse.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .paymentMethod(PaymentMethod.MOMO)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("50000.00"))
                .currency("VND")
                .paidAt(Instant.now())
                .build();

        when(paymentService.getPaymentStatus(eq(paymentId), eq(userId), eq(Role.ROLE_USER))).thenReturn(mockResponse);

        ResponseEntity<PaymentStatusResponse> response = paymentController.getPaymentStatus(paymentId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PaymentStatus.SUCCESS, response.getBody().getStatus());
        verify(paymentService).getPaymentStatus(paymentId, userId, Role.ROLE_USER);
    }
}

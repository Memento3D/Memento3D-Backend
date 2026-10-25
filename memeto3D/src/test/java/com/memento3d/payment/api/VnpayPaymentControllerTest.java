package com.memento3d.payment.api;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.payment.application.service.VnpayPaymentService;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentRequest;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayIpnResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayReturnResponse;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentControllerTest {

    @Mock
    private VnpayPaymentService vnpayPaymentService;

    @Mock
    private HttpServletRequest servletRequest;

    @InjectMocks
    private VnpayPaymentController vnpayPaymentController;

    private CustomUserDetails mockUserDetails;
    private UUID userId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("customer@memento3d.com")
                .password("password")
                .fullName("Customer Memento3D")
                .role(Role.ROLE_USER)
                .build();
        mockUserDetails = new CustomUserDetails(user);
    }

    @Test
    @DisplayName("POST /create returns 201 Created and paymentUrl")
    void testCreatePaymentEndpoint() {
        CreateVnpayPaymentRequest request = CreateVnpayPaymentRequest.builder()
                .orderId(orderId)
                .build();

        CreateVnpayPaymentResponse mockResponse = CreateVnpayPaymentResponse.builder()
                .paymentId(UUID.randomUUID())
                .orderId(orderId)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("550000.00"))
                .currency("VND")
                .transactionRef("M3D-ORD-123")
                .paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=55000000&vnp_SecureHash=abc")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(vnpayPaymentService.createPayment(any(), eq(userId), any())).thenReturn(mockResponse);

        ResponseEntity<CreateVnpayPaymentResponse> entity = vnpayPaymentController.createPayment(
                request, mockUserDetails, servletRequest);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getPaymentUrl()).contains("vnp_SecureHash=");
        assertThat(entity.getBody().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("GET /return verifies parameters and returns VnpayReturnResponse")
    void testReturnEndpoint() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "M3D-ORD-123");
        params.put("vnp_ResponseCode", "00");

        when(servletRequest.getParameterNames()).thenReturn(Collections.enumeration(params.keySet()));

        VnpayReturnResponse mockResponse = VnpayReturnResponse.builder()
                .orderId(orderId)
                .transactionRef("M3D-ORD-123")
                .status(PaymentStatus.SUCCESS)
                .message("Giao dịch thành công")
                .build();

        when(vnpayPaymentService.processReturn(any())).thenReturn(mockResponse);

        ResponseEntity<VnpayReturnResponse> entity = vnpayPaymentController.handleVnpayReturn(params, servletRequest);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("GET /ipn processes webhook and returns standard RspCode 00")
    void testIpnGetEndpoint() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "M3D-ORD-123");

        when(servletRequest.getParameterNames()).thenReturn(Collections.enumeration(params.keySet()));

        when(vnpayPaymentService.processIpn(any())).thenReturn(VnpayIpnResponse.success());

        ResponseEntity<VnpayIpnResponse> entity = vnpayPaymentController.handleVnpayIpnGet(params, servletRequest);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getRspCode()).isEqualTo("00");
        assertThat(entity.getBody().getMessage()).isEqualTo("Confirm Success");
    }
}

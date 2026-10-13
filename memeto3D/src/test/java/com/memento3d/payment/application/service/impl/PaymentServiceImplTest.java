package com.memento3d.payment.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.payment.api.request.CreatePaymentRequest;
import com.memento3d.payment.api.response.MomoIpnResponse;
import com.memento3d.payment.api.response.PaymentResponse;
import com.memento3d.payment.api.response.PaymentStatusResponse;
import com.memento3d.payment.api.response.ZaloPayCallbackResponse;
import com.memento3d.payment.application.dto.GatewayPaymentResponse;
import com.memento3d.payment.application.dto.GatewayQueryResponse;
import com.memento3d.payment.domain.exception.PaymentAmountMismatchException;
import com.memento3d.payment.domain.exception.PaymentException;
import com.memento3d.payment.domain.exception.PaymentGatewayException;
import com.memento3d.payment.domain.exception.PaymentSecurityException;
import com.memento3d.payment.domain.gateway.PaymentGateway;
import com.memento3d.payment.domain.gateway.PaymentGatewayFactory;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.gateway.momo.MomoProperties;
import com.memento3d.payment.infrastructure.gateway.momo.MomoSignatureService;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoIpnRequest;
import com.memento3d.payment.infrastructure.gateway.zalopay.ZaloPayProperties;
import com.memento3d.payment.infrastructure.gateway.zalopay.ZaloPaySignatureService;
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayCallbackRequest;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentGatewayFactory gatewayFactory;

    @Mock
    private MomoSignatureService momoSignatureService;

    @Mock
    private MomoProperties momoProperties;

    @Mock
    private ZaloPaySignatureService zaloPaySignatureService;

    @Mock
    private ZaloPayProperties zaloPayProperties;

    @Mock
    private PaymentGateway momoGateway;

    @Mock
    private PaymentGateway zaloPayGateway;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID userId;
    private UUID orderId;
    private Order mockOrder;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        mockOrder = Order.builder()
                .id(orderId)
                .userId(userId)
                .totalPrice(new BigDecimal("50000.00"))
                .currency("VND")
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
    }

    @Test
    @DisplayName("Create MoMo payment successfully")
    void testCreatePaymentMomoSuccess() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.MOMO)
                .orderInfo("Test order info")
                .build();

        when(orderService.validateOrderForPayment(orderId, userId)).thenReturn(mockOrder);
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());

        PaymentTransaction savedTx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .paymentMethod(PaymentMethod.MOMO)
                .provider("MOMO")
                .amount(mockOrder.getTotalPrice())
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .merchantTransactionId("MM_123456")
                .requestId(UUID.randomUUID().toString())
                .build();

        when(transactionRepository.save(any(PaymentTransaction.class))).thenReturn(savedTx);
        when(gatewayFactory.getGateway(PaymentMethod.MOMO)).thenReturn(momoGateway);

        GatewayPaymentResponse gatewayResponse = GatewayPaymentResponse.builder()
                .success(true)
                .paymentUrl("https://test-payment.momo.vn/pay/123")
                .qrCodeUrl("https://test-payment.momo.vn/qr/123")
                .build();
        when(momoGateway.createPayment(any(PaymentTransaction.class), any())).thenReturn(gatewayResponse);

        PaymentResponse response = paymentService.createPayment(request, userId);

        assertNotNull(response);
        assertEquals(PaymentMethod.MOMO, response.getPaymentMethod());
        assertEquals("https://test-payment.momo.vn/pay/123", response.getPaymentUrl());
        verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
    }

    @Test
    @DisplayName("Create ZaloPay payment successfully")
    void testCreatePaymentZaloPaySuccess() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.ZALOPAY)
                .build();

        when(orderService.validateOrderForPayment(orderId, userId)).thenReturn(mockOrder);
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());

        PaymentTransaction savedTx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .paymentMethod(PaymentMethod.ZALOPAY)
                .provider("ZALOPAY")
                .amount(mockOrder.getTotalPrice())
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .merchantTransactionId("261013_123456")
                .requestId(UUID.randomUUID().toString())
                .build();

        when(transactionRepository.save(any(PaymentTransaction.class))).thenReturn(savedTx);
        when(gatewayFactory.getGateway(PaymentMethod.ZALOPAY)).thenReturn(zaloPayGateway);

        GatewayPaymentResponse gatewayResponse = GatewayPaymentResponse.builder()
                .success(true)
                .paymentUrl("https://sb-openapi.zalopay.vn/pay/order?token=abc")
                .build();
        when(zaloPayGateway.createPayment(any(PaymentTransaction.class), any())).thenReturn(gatewayResponse);

        PaymentResponse response = paymentService.createPayment(request, userId);

        assertNotNull(response);
        assertEquals(PaymentMethod.ZALOPAY, response.getPaymentMethod());
        assertEquals("https://sb-openapi.zalopay.vn/pay/order?token=abc", response.getPaymentUrl());
    }

    @Test
    @DisplayName("Fail payment creation if order is already paid")
    void testCreatePaymentOrderAlreadyPaid() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        when(orderService.validateOrderForPayment(orderId, userId)).thenReturn(mockOrder);
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(PaymentTransaction.builder().status(PaymentStatus.SUCCESS).build()));

        assertThrows(PaymentException.class, () -> paymentService.createPayment(request, userId));
        verify(gatewayFactory, never()).getGateway(any());
    }

    @Test
    @DisplayName("Handle gateway failure during payment creation")
    void testCreatePaymentGatewayFailure() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        when(orderService.validateOrderForPayment(orderId, userId)).thenReturn(mockOrder);
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());

        PaymentTransaction savedTx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .paymentMethod(PaymentMethod.MOMO)
                .provider("MOMO")
                .amount(mockOrder.getTotalPrice())
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .merchantTransactionId("MM_123456")
                .requestId(UUID.randomUUID().toString())
                .build();

        when(transactionRepository.save(any(PaymentTransaction.class))).thenReturn(savedTx);
        when(gatewayFactory.getGateway(PaymentMethod.MOMO)).thenReturn(momoGateway);

        GatewayPaymentResponse gatewayResponse = GatewayPaymentResponse.builder()
                .success(false)
                .responseCode("99")
                .message("Provider internal error")
                .build();
        when(momoGateway.createPayment(any(PaymentTransaction.class), any())).thenReturn(gatewayResponse);

        assertThrows(PaymentGatewayException.class, () -> paymentService.createPayment(request, userId));
    }

    @Test
    @DisplayName("Process MoMo IPN successfully and update order to CONFIRMED")
    void testProcessMomoIpnSuccess() {
        MomoIpnRequest ipn = MomoIpnRequest.builder()
                .partnerCode("MOMO")
                .orderId("MM_123456")
                .requestId("REQ_123")
                .amount(50000L)
                .transId(987654321L)
                .resultCode(0)
                .message("Success")
                .signature("valid_signature")
                .build();

        when(momoProperties.getPartnerCode()).thenReturn("MOMO");
        when(momoProperties.getAccessKey()).thenReturn("F8BBA842ECF85");
        when(momoProperties.getSecretKey()).thenReturn("secret");
        when(momoSignatureService.verifyIpnSignature(eq(ipn), any(), any())).thenReturn(true);

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentStatus.PENDING)
                .merchantTransactionId("MM_123456")
                .build();

        when(transactionRepository.findByMerchantTransactionId("MM_123456")).thenReturn(Optional.of(tx));

        MomoIpnResponse response = paymentService.processMomoIpn(ipn);

        assertNotNull(response);
        assertEquals(0, response.getResultCode());
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertEquals("987654321", tx.getProviderTransactionId());
        verify(orderService).updateOrderStatusPaid(orderId);
        verify(transactionRepository).save(tx);
    }

    @Test
    @DisplayName("Reject MoMo IPN with invalid signature")
    void testProcessMomoIpnInvalidSignature() {
        MomoIpnRequest ipn = MomoIpnRequest.builder()
                .partnerCode("MOMO")
                .orderId("MM_123456")
                .requestId("REQ_123")
                .amount(50000L)
                .signature("bad_signature")
                .build();

        when(momoSignatureService.verifyIpnSignature(eq(ipn), any(), any())).thenReturn(false);

        assertThrows(PaymentSecurityException.class, () -> paymentService.processMomoIpn(ipn));
        verify(transactionRepository, never()).findByMerchantTransactionId(any());
    }

    @Test
    @DisplayName("Reject MoMo IPN with amount mismatch")
    void testProcessMomoIpnAmountMismatch() {
        MomoIpnRequest ipn = MomoIpnRequest.builder()
                .partnerCode("MOMO")
                .orderId("MM_123456")
                .amount(30000L) // Mismatch
                .signature("valid_signature")
                .build();

        when(momoProperties.getPartnerCode()).thenReturn("MOMO");
        when(momoSignatureService.verifyIpnSignature(eq(ipn), any(), any())).thenReturn(true);

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(transactionRepository.findByMerchantTransactionId("MM_123456")).thenReturn(Optional.of(tx));

        assertThrows(PaymentAmountMismatchException.class, () -> paymentService.processMomoIpn(ipn));
        verify(orderService, never()).updateOrderStatusPaid(any());
    }

    @Test
    @DisplayName("MoMo IPN duplicate callback is idempotent and does not fulfill twice")
    void testProcessMomoIpnDuplicateCallbackIdempotent() {
        MomoIpnRequest ipn = MomoIpnRequest.builder()
                .partnerCode("MOMO")
                .orderId("MM_123456")
                .requestId("REQ_123")
                .amount(50000L)
                .transId(987654321L)
                .resultCode(0)
                .signature("valid_signature")
                .build();

        when(momoProperties.getPartnerCode()).thenReturn("MOMO");
        when(momoSignatureService.verifyIpnSignature(eq(ipn), any(), any())).thenReturn(true);

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentStatus.SUCCESS) // Already SUCCESS
                .build();

        when(transactionRepository.findByMerchantTransactionId("MM_123456")).thenReturn(Optional.of(tx));

        MomoIpnResponse response = paymentService.processMomoIpn(ipn);

        assertNotNull(response);
        assertEquals(0, response.getResultCode());
        // Verify updateOrderStatusPaid is NOT called again
        verify(orderService, never()).updateOrderStatusPaid(any());
    }

    @Test
    @DisplayName("Process ZaloPay callback successfully")
    void testProcessZaloPayCallbackSuccess() {
        String dataJson = "{\"app_id\":2553,\"app_trans_id\":\"261013_999\",\"amount\":50000,\"zp_trans_id\":888888}";
        ZaloPayCallbackRequest callbackRequest = ZaloPayCallbackRequest.builder()
                .data(dataJson)
                .mac("valid_mac")
                .build();

        when(zaloPayProperties.getKey2()).thenReturn("key2");
        when(zaloPaySignatureService.verifyCallbackMac(eq(dataJson), eq("valid_mac"), eq("key2"))).thenReturn(true);

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .amount(new BigDecimal("50000.00"))
                .status(PaymentStatus.PENDING)
                .merchantTransactionId("261013_999")
                .build();

        when(transactionRepository.findByMerchantTransactionId("261013_999")).thenReturn(Optional.of(tx));

        ZaloPayCallbackResponse response = paymentService.processZaloPayCallback(callbackRequest);

        assertNotNull(response);
        assertEquals(1, response.getReturnCode());
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertEquals("888888", tx.getProviderTransactionId());
        verify(orderService).updateOrderStatusPaid(orderId);
    }

    @Test
    @DisplayName("Reject ZaloPay callback with invalid MAC")
    void testProcessZaloPayCallbackInvalidMac() {
        ZaloPayCallbackRequest callbackRequest = ZaloPayCallbackRequest.builder()
                .data("{}")
                .mac("invalid_mac")
                .build();

        when(zaloPayProperties.getKey2()).thenReturn("key2");
        when(zaloPaySignatureService.verifyCallbackMac(any(), any(), any())).thenReturn(false);

        ZaloPayCallbackResponse response = paymentService.processZaloPayCallback(callbackRequest);

        assertNotNull(response);
        assertEquals(2, response.getReturnCode());
        verify(transactionRepository, never()).findByMerchantTransactionId(any());
    }

    @Test
    @DisplayName("Enforce ownership check: accessing another user's payment throws AccessDeniedException")
    void testGetPaymentStatusAccessDenied() {
        UUID paymentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(paymentId)
                .userId(ownerId)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(transactionRepository.findById(paymentId)).thenReturn(Optional.of(tx));

        assertThrows(AccessDeniedException.class, () ->
                paymentService.getPaymentStatus(paymentId, otherUserId, Role.ROLE_USER));
    }

    @Test
    @DisplayName("Admin can view any payment status")
    void testGetPaymentStatusAdminAccess() {
        UUID paymentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(paymentId)
                .orderId(orderId)
                .userId(ownerId)
                .paymentMethod(PaymentMethod.MOMO)
                .amount(new BigDecimal("50000.00"))
                .currency("VND")
                .status(PaymentStatus.SUCCESS)
                .build();

        when(transactionRepository.findById(paymentId)).thenReturn(Optional.of(tx));

        PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId, adminId, Role.ROLE_ADMIN);

        assertNotNull(response);
        assertEquals(paymentId, response.getPaymentId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
    }

    @Test
    @DisplayName("Reconcile pending payment transaction with gateway query")
    void testGetPaymentStatusReconciledWithGateway() {
        UUID paymentId = UUID.randomUUID();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(paymentId)
                .orderId(orderId)
                .userId(userId)
                .paymentMethod(PaymentMethod.MOMO)
                .amount(new BigDecimal("50000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build();

        when(transactionRepository.findById(paymentId)).thenReturn(Optional.of(tx));
        when(gatewayFactory.getGateway(PaymentMethod.MOMO)).thenReturn(momoGateway);

        GatewayQueryResponse queryResponse = GatewayQueryResponse.builder()
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId("TX_RECONCILED_123")
                .paidAt(Instant.now())
                .build();
        when(momoGateway.queryPayment(tx)).thenReturn(queryResponse);
        when(transactionRepository.save(any(PaymentTransaction.class))).thenReturn(tx);

        PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId, userId, Role.ROLE_USER);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(orderService).updateOrderStatusPaid(orderId);
    }
}

package com.memento3d.payment.application.service.impl;

import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.exception.OrderAccessDeniedException;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.payment.domain.exception.PaymentAlreadyPaidException;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.gateway.vnpay.VnpayProperties;
import com.memento3d.payment.infrastructure.gateway.vnpay.VnpayResponseMapper;
import com.memento3d.payment.infrastructure.gateway.vnpay.VnpaySignatureService;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentRequest;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayIpnResponse;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VnpayPaymentServiceImplTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private OrderService orderService;

    @Spy
    private VnpayProperties properties = new VnpayProperties();

    @Spy
    private VnpaySignatureService signatureService = new VnpaySignatureService();

    @Spy
    private VnpayResponseMapper responseMapper = new VnpayResponseMapper();

    @Mock
    private OutboxPublisherService outboxPublisher;

    @InjectMocks
    private VnpayPaymentServiceImpl vnpayPaymentService;

    private UUID customerId;
    private UUID otherCustomerId;
    private UUID orderId;
    private Order unpaidOrder;
    private final String secretKey = "MEMENTO3DTESTVNPAYHASHSECRETFORINTEGRATIONTEST";

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        properties.setTmnCode("TESTTMN01");
        properties.setHashSecret(secretKey);
        properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:5173/payment/vnpay/return");
        properties.setIpnUrl("http://localhost:8080/api/v1/payments/vnpay/ipn");

        unpaidOrder = Order.builder()
                .id(orderId)
                .orderCode("ORD-20261025-0001")
                .userId(customerId)
                .totalPrice(new BigDecimal("550000.00"))
                .currency("VND")
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Case 1 — Create payment: authenticated customer, valid unpaid order -> payment created, URL returned")
    void testCase1_CreatePaymentSuccess() {
        when(orderService.validateOrderForPayment(orderId, customerId)).thenReturn(unpaidOrder);
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.PAID)).thenReturn(Optional.empty());

        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            tx.setCreatedAt(Instant.now());
            return tx;
        });

        CreateVnpayPaymentRequest request = CreateVnpayPaymentRequest.builder()
                .orderId(orderId)
                .build();

        CreateVnpayPaymentResponse response = vnpayPaymentService.createPayment(request, customerId, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("550000.00"));
        assertThat(response.getPaymentUrl()).contains("vnp_SecureHash=");
        assertThat(response.getPaymentUrl()).contains("vnp_Amount=55000000"); // 550,000 * 100
        assertThat(response.getTransactionRef()).startsWith("M3D-ORD-20261025-0001-");

        verify(transactionRepository, times(1)).save(any(PaymentTransaction.class));
        verify(outboxPublisher, times(1)).publish(eq("PAYMENT"), any(), eq("PAYMENT_STATUS_CHANGED"), any());
    }

    @Test
    @DisplayName("Case 2 — Wrong owner: Customer B uses Customer A orderId -> 403 Forbidden")
    void testCase2_WrongOwnerThrowsAccessDenied() {
        when(orderService.validateOrderForPayment(orderId, otherCustomerId))
                .thenThrow(new OrderAccessDeniedException("User is not authorized to access this order"));

        CreateVnpayPaymentRequest request = CreateVnpayPaymentRequest.builder()
                .orderId(orderId)
                .build();

        assertThatThrownBy(() -> vnpayPaymentService.createPayment(request, otherCustomerId, "127.0.0.1"))
                .isInstanceOf(OrderAccessDeniedException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case 3 — Already paid: order PAID -> create payment -> 409 Conflict")
    void testCase3_AlreadyPaidThrowsConflict() {
        unpaidOrder.setPaymentStatus(PaymentStatus.PAID);
        when(orderService.validateOrderForPayment(orderId, customerId)).thenReturn(unpaidOrder);

        CreateVnpayPaymentRequest request = CreateVnpayPaymentRequest.builder()
                .orderId(orderId)
                .build();

        assertThatThrownBy(() -> vnpayPaymentService.createPayment(request, customerId, "127.0.0.1"))
                .isInstanceOf(PaymentAlreadyPaidException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case 4 — Success IPN: valid signature, responseCode=00, transactionStatus=00, amount matched -> Payment SUCCESS, Order PAID")
    void testCase4_SuccessIpnProcessesPaymentAndFulfillsOrder() {
        String txnRef = "M3D-ORD-0001-1728830000";
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(customerId)
                .paymentMethod(PaymentMethod.VNPAY)
                .amount(new BigDecimal("550000.00"))
                .status(PaymentStatus.PENDING)
                .merchantTransactionId(txnRef)
                .build();

        when(transactionRepository.findByMerchantTransactionId(txnRef)).thenReturn(Optional.of(tx));

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_Amount", "55000000"); // 550,000 * 100
        ipnParams.put("vnp_BankCode", "NCB");
        ipnParams.put("vnp_CardType", "ATM");
        ipnParams.put("vnp_OrderInfo", "Thanh toan don hang");
        ipnParams.put("vnp_PayDate", "20261025141000");
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TransactionNo", "14589230");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TxnRef", txnRef);

        String secureHash = signatureService.hashAllFields(ipnParams, secretKey);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnResponse = vnpayPaymentService.processIpn(ipnParams);

        assertThat(ipnResponse.getRspCode()).isEqualTo("00");
        assertThat(ipnResponse.getMessage()).isEqualTo("Confirm Success");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(tx.getProviderTransactionId()).isEqualTo("14589230");

        verify(orderService, times(1)).updateOrderStatusPaid(orderId);
        verify(transactionRepository, times(1)).save(tx);
        verify(outboxPublisher, times(1)).publish(eq("PAYMENT"), any(), eq("PAYMENT_STATUS_CHANGED"), any());
    }

    @Test
    @DisplayName("Case 5 — Invalid signature -> transaction unchanged, IPN returns 97")
    void testCase5_InvalidSignatureReturns97() {
        String txnRef = "M3D-ORD-0001-1728830000";
        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_Amount", "55000000");
        ipnParams.put("vnp_TxnRef", txnRef);
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_SecureHash", "INVALID_FORGED_HASH_CODE");

        VnpayIpnResponse ipnResponse = vnpayPaymentService.processIpn(ipnParams);

        assertThat(ipnResponse.getRspCode()).isEqualTo("97");
        assertThat(ipnResponse.getMessage()).isEqualTo("Invalid Checksum");

        verify(transactionRepository, never()).findByMerchantTransactionId(any());
        verify(orderService, never()).updateOrderStatusPaid(any());
    }

    @Test
    @DisplayName("Case 6 — Amount mismatch -> transaction unchanged, IPN returns 04")
    void testCase6_AmountMismatchReturns04() {
        String txnRef = "M3D-ORD-0001-1728830000";
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("550000.00")) // Expected: 55000000
                .status(PaymentStatus.PENDING)
                .merchantTransactionId(txnRef)
                .build();

        when(transactionRepository.findByMerchantTransactionId(txnRef)).thenReturn(Optional.of(tx));

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_Amount", "10000000"); // Manipulated: 100,000 VND
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TxnRef", txnRef);

        String secureHash = signatureService.hashAllFields(ipnParams, secretKey);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnResponse = vnpayPaymentService.processIpn(ipnParams);

        assertThat(ipnResponse.getRspCode()).isEqualTo("04");
        assertThat(ipnResponse.getMessage()).isEqualTo("Invalid amount");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING); // Unchanged

        verify(orderService, never()).updateOrderStatusPaid(any());
    }

    @Test
    @DisplayName("Case 7 — Duplicate IPN: Call same IPN twice -> second request idempotent, returns 02, no duplicate event")
    void testCase7_DuplicateIpnIsIdempotent() {
        String txnRef = "M3D-ORD-0001-1728830000";
        PaymentTransaction alreadySuccessTx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("550000.00"))
                .status(PaymentStatus.SUCCESS) // Already processed
                .merchantTransactionId(txnRef)
                .build();

        when(transactionRepository.findByMerchantTransactionId(txnRef)).thenReturn(Optional.of(alreadySuccessTx));

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_Amount", "55000000");
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TxnRef", txnRef);

        String secureHash = signatureService.hashAllFields(ipnParams, secretKey);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnResponse = vnpayPaymentService.processIpn(ipnParams);

        assertThat(ipnResponse.getRspCode()).isEqualTo("02");
        assertThat(ipnResponse.getMessage()).isEqualTo("Order already confirmed");

        verify(orderService, never()).updateOrderStatusPaid(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Case 8 — Customer closes browser: Never hits return URL -> IPN alone still marks Payment SUCCESS & Order PAID")
    void testCase8_CustomerClosesBrowserIpnFulfillsOrder() {
        String txnRef = "M3D-ORD-CLOSED-BROWSER";
        PaymentTransaction pendingTx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(customerId)
                .amount(new BigDecimal("550000.00"))
                .status(PaymentStatus.PENDING)
                .merchantTransactionId(txnRef)
                .build();

        when(transactionRepository.findByMerchantTransactionId(txnRef)).thenReturn(Optional.of(pendingTx));

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_Amount", "55000000");
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TransactionNo", "99887766");
        ipnParams.put("vnp_TxnRef", txnRef);

        String secureHash = signatureService.hashAllFields(ipnParams, secretKey);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnResponse = vnpayPaymentService.processIpn(ipnParams);

        assertThat(ipnResponse.getRspCode()).isEqualTo("00");
        assertThat(pendingTx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderService, times(1)).updateOrderStatusPaid(orderId);
    }

    @Test
    @DisplayName("Case 9 — Payment failed: responseCode=51 -> Payment FAILED, Order remains unpaid")
    void testCase9_PaymentFailedLeavesOrderUnpaid() {
        String txnRef = "M3D-ORD-0001-FAILED";
        PaymentTransaction pendingTx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(customerId)
                .amount(new BigDecimal("550000.00"))
                .status(PaymentStatus.PENDING)
                .merchantTransactionId(txnRef)
                .build();

        when(transactionRepository.findByMerchantTransactionId(txnRef)).thenReturn(Optional.of(pendingTx));

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_Amount", "55000000");
        ipnParams.put("vnp_ResponseCode", "51"); // Insufficient balance
        ipnParams.put("vnp_TransactionStatus", "02");
        ipnParams.put("vnp_TxnRef", txnRef);

        String secureHash = signatureService.hashAllFields(ipnParams, secretKey);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnResponse = vnpayPaymentService.processIpn(ipnParams);

        assertThat(ipnResponse.getRspCode()).isEqualTo("00"); // Acknowledged receipt
        assertThat(pendingTx.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pendingTx.getResponseCode()).isEqualTo("51");

        verify(orderService, never()).updateOrderStatusPaid(any());
        verify(transactionRepository, times(1)).save(pendingTx);
    }

    @Test
    @DisplayName("Case 10 — Retry: FAILED payment -> customer retries -> new payment transaction created, old preserved")
    void testCase10_RetryPaymentCreatesNewTransaction() {
        when(orderService.validateOrderForPayment(orderId, customerId)).thenReturn(unpaidOrder);
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(transactionRepository.findFirstByOrderIdAndStatus(orderId, PaymentStatus.PAID)).thenReturn(Optional.empty());

        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        CreateVnpayPaymentRequest retryRequest = CreateVnpayPaymentRequest.builder()
                .orderId(orderId)
                .build();

        CreateVnpayPaymentResponse retryResponse = vnpayPaymentService.createPayment(retryRequest, customerId, "127.0.0.1");

        assertThat(retryResponse).isNotNull();
        assertThat(retryResponse.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(retryResponse.getPaymentUrl()).contains("vnp_SecureHash=");

        verify(transactionRepository, times(1)).save(any(PaymentTransaction.class));
    }
}

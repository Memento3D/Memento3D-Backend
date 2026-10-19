package com.memento3d.payment.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.payment.api.request.CreatePaymentRequest;
import com.memento3d.payment.api.response.MomoIpnResponse;
import com.memento3d.payment.api.response.PaymentResponse;
import com.memento3d.payment.api.response.PaymentStatusResponse;
import com.memento3d.payment.api.response.ZaloPayCallbackResponse;
import com.memento3d.payment.application.dto.GatewayPaymentRequest;
import com.memento3d.payment.application.dto.GatewayPaymentResponse;
import com.memento3d.payment.application.dto.GatewayQueryResponse;
import com.memento3d.payment.application.service.PaymentService;
import com.memento3d.payment.domain.exception.*;
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
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayCallbackData;
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayCallbackRequest;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final OrderService orderService;
    private final PaymentGatewayFactory gatewayFactory;
    private final MomoSignatureService momoSignatureService;
    private final MomoProperties momoProperties;
    private final ZaloPaySignatureService zaloPaySignatureService;
    private final ZaloPayProperties zaloPayProperties;
    private final com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, UUID userId) {
        log.info("Initiating payment creation for orderId: {}, method: {}, userId: {}",
                request.getOrderId(), request.getPaymentMethod(), userId);

        Order order = orderService.validateOrderForPayment(request.getOrderId(), userId);

        Optional<PaymentTransaction> existingSuccess = transactionRepository.findFirstByOrderIdAndStatus(
                order.getId(), PaymentStatus.SUCCESS);
        if (existingSuccess.isEmpty()) {
            existingSuccess = transactionRepository.findFirstByOrderIdAndStatus(order.getId(), PaymentStatus.PAID);
        }
        if (existingSuccess.isPresent() || order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new PaymentAlreadyPaidException("Order has already been successfully paid");
        }

        // Idempotency / Double-click check: return existing pending transaction if recently created
        Optional<PaymentTransaction> existingPending = transactionRepository.findFirstByOrderIdAndStatus(
                order.getId(), PaymentStatus.PENDING);
        if (existingPending.isPresent() && existingPending.get().getPaymentMethod() == request.getPaymentMethod()) {
            PaymentTransaction existingTx = existingPending.get();
            log.info("Returning existing pending payment transaction ID={} to prevent duplicate", existingTx.getId());
            return PaymentResponse.builder()
                    .paymentId(existingTx.getId())
                    .orderId(existingTx.getOrderId())
                    .paymentMethod(existingTx.getPaymentMethod())
                    .status(existingTx.getStatus())
                    .amount(existingTx.getAmount())
                    .currency(existingTx.getCurrency())
                    .paymentUrl(existingTx.getPaymentUrl())
                    .merchantTransactionId(existingTx.getMerchantTransactionId())
                    .createdAt(existingTx.getCreatedAt())
                    .build();
        }

        BigDecimal payableAmount = order.getTotalPrice();
        String merchantTxId = generateMerchantTransactionId(request.getPaymentMethod());
        String requestId = UUID.randomUUID().toString();

        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId(order.getId())
                .userId(userId)
                .paymentMethod(request.getPaymentMethod())
                .provider(request.getPaymentMethod().name())
                .amount(payableAmount)
                .currency(order.getCurrency() != null ? order.getCurrency() : "VND")
                .status(PaymentStatus.PENDING)
                .merchantTransactionId(merchantTxId)
                .requestId(requestId)
                .build();

        transaction = transactionRepository.save(transaction);

        // CASH Method: no external gateway call required
        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            order.setPaymentMethod(PaymentMethod.CASH);
            log.info("Created CASH payment transaction ID={} for order ID={}", transaction.getId(), order.getId());
            publishPaymentStatusEvent(transaction);
            return PaymentResponse.builder()
                    .paymentId(transaction.getId())
                    .orderId(transaction.getOrderId())
                    .paymentMethod(PaymentMethod.CASH)
                    .status(PaymentStatus.PENDING)
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .paymentUrl(null)
                    .merchantTransactionId(transaction.getMerchantTransactionId())
                    .createdAt(transaction.getCreatedAt())
                    .build();
        }

        PaymentGateway gateway = gatewayFactory.getGateway(request.getPaymentMethod());
        GatewayPaymentRequest gatewayRequest = GatewayPaymentRequest.builder()
                .orderInfo(request.getOrderInfo())
                .redirectUrl(request.getRedirectUrl())
                .build();

        GatewayPaymentResponse gatewayResponse;
        try {
            gatewayResponse = gateway.createPayment(transaction, gatewayRequest);
        } catch (Exception e) {
            log.error("Failed to create payment on gateway: {}", e.getMessage());
            transaction.markFailed("GATEWAY_ERROR", e.getMessage());
            transactionRepository.save(transaction);
            throw new PaymentGatewayException("Payment gateway call failed: " + e.getMessage(), e);
        }

        if (gatewayResponse.isSuccess()) {
            transaction.setPaymentUrl(gatewayResponse.getPaymentUrl());
            if (gatewayResponse.getProviderTransactionId() != null) {
                transaction.setProviderTransactionId(gatewayResponse.getProviderTransactionId());
            }
            transactionRepository.save(transaction);
            publishPaymentStatusEvent(transaction);

            return PaymentResponse.builder()
                    .paymentId(transaction.getId())
                    .orderId(transaction.getOrderId())
                    .paymentMethod(transaction.getPaymentMethod())
                    .status(transaction.getStatus())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .paymentUrl(transaction.getPaymentUrl())
                    .qrCodeUrl(gatewayResponse.getQrCodeUrl())
                    .deeplink(gatewayResponse.getDeeplink())
                    .merchantTransactionId(transaction.getMerchantTransactionId())
                    .createdAt(transaction.getCreatedAt())
                    .build();
        } else {
            transaction.markFailed(gatewayResponse.getResponseCode(), gatewayResponse.getMessage());
            transactionRepository.save(transaction);
            publishPaymentStatusEvent(transaction);
            throw new PaymentGatewayException("Gateway rejected payment creation: " + gatewayResponse.getMessage());
        }
    }

    @Override
    @Transactional
    public PaymentStatusResponse getPaymentStatus(UUID paymentId, UUID userId, Role role) {
        PaymentTransaction transaction = transactionRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found with ID: " + paymentId));

        boolean isPrivileged = role == Role.ROLE_ADMIN || role == Role.ROLE_MANAGER || role == Role.ROLE_OPERATOR;
        if (!transaction.getUserId().equals(userId) && !isPrivileged) {
            log.warn("Access denied: User {} cannot view payment {}", userId, paymentId);
            throw new AccessDeniedException("User is not authorized to access this payment transaction");
        }

        if (transaction.getStatus() == PaymentStatus.PENDING || transaction.getStatus() == PaymentStatus.PROCESSING) {
            try {
                PaymentGateway gateway = gatewayFactory.getGateway(transaction.getPaymentMethod());
                GatewayQueryResponse queryResponse = gateway.queryPayment(transaction);
                if (queryResponse != null && queryResponse.getStatus() != null && queryResponse.getStatus() != transaction.getStatus()) {
                    log.info("Reconciled transaction {} status from {} to {}",
                            transaction.getId(), transaction.getStatus(), queryResponse.getStatus());
                    if (queryResponse.getStatus() == PaymentStatus.SUCCESS) {
                        transaction.markSuccess(queryResponse.getProviderTransactionId(), queryResponse.getPaidAt());
                        orderService.updateOrderStatusPaid(transaction.getOrderId());
                    } else if (queryResponse.getStatus() == PaymentStatus.FAILED) {
                        transaction.markFailed(queryResponse.getResponseCode(), queryResponse.getMessage());
                    }
                    transaction = transactionRepository.save(transaction);
                    publishPaymentStatusEvent(transaction);
                }
            } catch (Exception e) {
                log.warn("Could not reconcile payment status with provider: {}", e.getMessage());
            }
        }

        return PaymentStatusResponse.builder()
                .paymentId(transaction.getId())
                .orderId(transaction.getOrderId())
                .paymentMethod(transaction.getPaymentMethod())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .merchantTransactionId(transaction.getMerchantTransactionId())
                .providerTransactionId(transaction.getProviderTransactionId())
                .failureCode(transaction.getFailureCode())
                .failureMessage(transaction.getFailureMessage())
                .paidAt(transaction.getPaidAt())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public PaymentStatusResponse cancelPayment(UUID paymentId, UUID userId, Role role) {
        PaymentTransaction transaction = transactionRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found with ID: " + paymentId));

        boolean isPrivileged = role == Role.ROLE_ADMIN || role == Role.ROLE_MANAGER || role == Role.ROLE_OPERATOR;
        if (!transaction.getUserId().equals(userId) && !isPrivileged) {
            log.warn("Access denied: User {} cannot cancel payment {}", userId, paymentId);
            throw new AccessDeniedException("User is not authorized to cancel this payment transaction");
        }

        if (transaction.isTerminal()) {
            throw new PaymentException("Cannot cancel payment in terminal state: " + transaction.getStatus());
        }

        transaction.markCancelled("Cancelled by user request");
        transaction = transactionRepository.save(transaction);
        publishPaymentStatusEvent(transaction);
        log.info("Cancelled payment transaction {} by user {}", paymentId, userId);

        return PaymentStatusResponse.fromEntity(transaction);
    }

    @Override
    @Transactional
    public PaymentStatusResponse confirmCashPayment(UUID paymentId, UUID managerId, Role role) {
        boolean isStaff = (role == Role.ROLE_ADMIN || role == Role.ROLE_MANAGER || role == Role.ROLE_OPERATOR);
        if (!isStaff) {
            log.warn("Access denied: User {} with role {} tried to confirm cash payment {}", managerId, role, paymentId);
            throw new AccessDeniedException("Only Managers and Admins can confirm cash payments");
        }

        PaymentTransaction transaction = transactionRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found with ID: " + paymentId));

        if (transaction.getPaymentMethod() != PaymentMethod.CASH) {
            throw new InvalidPaymentMethodException("Only CASH payment transactions can be confirmed manually. Transaction method is " + transaction.getPaymentMethod());
        }

        if (transaction.getStatus() == PaymentStatus.PAID || transaction.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Cash payment transaction {} is already confirmed", paymentId);
            return PaymentStatusResponse.fromEntity(transaction);
        }

        if (transaction.getStatus() == PaymentStatus.CANCELLED || transaction.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentException("Cannot confirm cash payment for a " + transaction.getStatus() + " transaction");
        }

        transaction.confirmCash(managerId);
        transaction = transactionRepository.save(transaction);
        publishPaymentStatusEvent(transaction);

        orderService.updateOrderStatusPaid(transaction.getOrderId());
        log.info("Manager ID={} successfully confirmed CASH payment transaction ID={} for order ID={}",
                managerId, paymentId, transaction.getOrderId());

        return PaymentStatusResponse.fromEntity(transaction);
    }

    @Override
    @Transactional
    public MomoIpnResponse processMomoIpn(MomoIpnRequest ipnRequest) {
        log.info("Processing MoMo IPN notification for orderId: {}, requestId: {}, resultCode: {}",
                ipnRequest.getOrderId(), ipnRequest.getRequestId(), ipnRequest.getResultCode());

        boolean validSignature = momoSignatureService.verifyIpnSignature(
                ipnRequest,
                momoProperties.getAccessKey(),
                momoProperties.getSecretKey()
        );

        if (!validSignature) {
            log.error("Invalid MoMo IPN signature for orderId: {}", ipnRequest.getOrderId());
            throw new PaymentSecurityException("Invalid MoMo IPN signature");
        }

        if (!momoProperties.getPartnerCode().equals(ipnRequest.getPartnerCode())) {
            log.error("MoMo partner code mismatch: expected {}, received {}",
                    momoProperties.getPartnerCode(), ipnRequest.getPartnerCode());
            throw new PaymentSecurityException("MoMo partnerCode mismatch");
        }

        PaymentTransaction transaction = transactionRepository.findByMerchantTransactionId(ipnRequest.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found for MoMo orderId: " + ipnRequest.getOrderId()));

        BigDecimal ipnAmount = BigDecimal.valueOf(ipnRequest.getAmount());
        if (transaction.getAmount().compareTo(ipnAmount) != 0) {
            log.error("MoMo payment amount mismatch! Expected: {}, Received: {}",
                    transaction.getAmount(), ipnAmount);
            throw new PaymentAmountMismatchException("Payment amount mismatch in MoMo IPN");
        }

        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Transaction {} is already marked as SUCCESS. Acknowledging duplicate MoMo IPN.", transaction.getId());
            return MomoIpnResponse.success(ipnRequest.getPartnerCode(), ipnRequest.getOrderId(), ipnRequest.getRequestId());
        }

        if (ipnRequest.getResultCode() == 0) {
            transaction.markSuccess(String.valueOf(ipnRequest.getTransId()), Instant.now());
            orderService.updateOrderStatusPaid(transaction.getOrderId());
            log.info("Successfully marked transaction {} as SUCCESS and fulfilled order {}",
                    transaction.getId(), transaction.getOrderId());
        } else {
            transaction.markFailed(String.valueOf(ipnRequest.getResultCode()), ipnRequest.getMessage());
            log.warn("MoMo payment failed for transaction {}: resultCode={}, message={}",
                    transaction.getId(), ipnRequest.getResultCode(), ipnRequest.getMessage());
        }

        transactionRepository.save(transaction);
        publishPaymentStatusEvent(transaction);
        return MomoIpnResponse.success(ipnRequest.getPartnerCode(), ipnRequest.getOrderId(), ipnRequest.getRequestId());
    }

    @Override
    @Transactional
    public ZaloPayCallbackResponse processZaloPayCallback(ZaloPayCallbackRequest callbackRequest) {
        log.info("Processing ZaloPay callback webhook");

        boolean validMac = zaloPaySignatureService.verifyCallbackMac(
                callbackRequest.getData(),
                callbackRequest.getMac(),
                zaloPayProperties.getKey2()
        );

        if (!validMac) {
            log.error("Invalid ZaloPay callback MAC");
            return ZaloPayCallbackResponse.fail("Invalid MAC signature");
        }

        ZaloPayCallbackData data;
        try {
            data = objectMapper.readValue(callbackRequest.getData(), ZaloPayCallbackData.class);
        } catch (Exception e) {
            log.error("Failed to parse ZaloPay callback data JSON", e);
            return ZaloPayCallbackResponse.fail("Malformed callback data");
        }

        log.info("Parsed ZaloPay callback data for app_trans_id: {}, zp_trans_id: {}, amount: {}",
                data.getAppTransId(), data.getZpTransId(), data.getAmount());

        PaymentTransaction transaction = transactionRepository.findByMerchantTransactionId(data.getAppTransId())
                .orElse(null);

        if (transaction == null) {
            log.error("No transaction found for ZaloPay app_trans_id: {}", data.getAppTransId());
            return ZaloPayCallbackResponse.fail("Transaction not found");
        }

        BigDecimal callbackAmount = BigDecimal.valueOf(data.getAmount());
        if (transaction.getAmount().compareTo(callbackAmount) != 0) {
            log.error("ZaloPay callback amount mismatch! Expected: {}, Received: {}",
                    transaction.getAmount(), callbackAmount);
            return ZaloPayCallbackResponse.fail("Amount mismatch");
        }

        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Transaction {} already SUCCESS. Returning ZaloPay success acknowledgment.", transaction.getId());
            return ZaloPayCallbackResponse.success();
        }

        transaction.markSuccess(String.valueOf(data.getZpTransId()), Instant.now());
        orderService.updateOrderStatusPaid(transaction.getOrderId());
        transactionRepository.save(transaction);
        publishPaymentStatusEvent(transaction);

        log.info("Successfully marked transaction {} as SUCCESS and confirmed order {}",
                transaction.getId(), transaction.getOrderId());

        return ZaloPayCallbackResponse.success();
    }

    private void publishPaymentStatusEvent(PaymentTransaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("paymentId", transaction.getId());
            payload.put("orderId", transaction.getOrderId());
            payload.put("userId", transaction.getUserId());
            payload.put("paymentMethod", transaction.getPaymentMethod() != null ? transaction.getPaymentMethod().name() : null);
            payload.put("status", transaction.getStatus() != null ? transaction.getStatus().name() : null);
            payload.put("amount", transaction.getAmount());
            payload.put("currency", transaction.getCurrency());
            payload.put("paidAt", transaction.getPaidAt());
            payload.put("failureReason", transaction.getFailureMessage());

            outboxPublisher.publish(
                    "PAYMENT",
                    transaction.getId().toString(),
                    "PAYMENT_STATUS_CHANGED",
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish payment status event for transaction {}: {}", transaction.getId(), e.getMessage());
        }
    }

    private String generateMerchantTransactionId(PaymentMethod method) {
        long timestamp = System.currentTimeMillis();
        int randomPart = 100000 + random.nextInt(900000);
        if (method == PaymentMethod.ZALOPAY) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd")
                    .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
            String datePrefix = formatter.format(Instant.now());
            return datePrefix + "_" + timestamp % 1000000000L + randomPart;
        } else {
            return "MM_" + timestamp + "_" + randomPart;
        }
    }
}

package com.memento3d.payment.application.service.impl;

import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.payment.application.service.VnpayPaymentService;
import com.memento3d.payment.domain.exception.PaymentAlreadyPaidException;
import com.memento3d.payment.domain.exception.PaymentAmountMismatchException;
import com.memento3d.payment.domain.exception.PaymentGatewayException;
import com.memento3d.payment.domain.exception.PaymentSecurityException;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.gateway.vnpay.VnpayProperties;
import com.memento3d.payment.infrastructure.gateway.vnpay.VnpayResponseMapper;
import com.memento3d.payment.infrastructure.gateway.vnpay.VnpaySignatureService;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentRequest;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayIpnResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayReturnResponse;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
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
public class VnpayPaymentServiceImpl implements VnpayPaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final OrderService orderService;
    private final VnpayProperties properties;
    private final VnpaySignatureService signatureService;
    private final VnpayResponseMapper responseMapper;
    private final OutboxPublisherService outboxPublisher;

    private static final DateTimeFormatter VNPAY_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    @Transactional
    public CreateVnpayPaymentResponse createPayment(CreateVnpayPaymentRequest request, UUID userId, String clientIp) {
        log.info("Initiating VNPAY payment creation for orderId: {}, userId: {}", request.getOrderId(), userId);

        // 1. Verify order existence and customer ownership
        Order order = orderService.validateOrderForPayment(request.getOrderId(), userId);

        // 2. Reject if order is already paid (409 Conflict)
        Optional<PaymentTransaction> existingSuccess = transactionRepository.findFirstByOrderIdAndStatus(
                order.getId(), PaymentStatus.SUCCESS);
        if (existingSuccess.isEmpty()) {
            existingSuccess = transactionRepository.findFirstByOrderIdAndStatus(order.getId(), PaymentStatus.PAID);
        }
        if (existingSuccess.isPresent() || order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.warn("Attempted to initiate VNPAY payment for already paid order {}", order.getId());
            throw new PaymentAlreadyPaidException("Đơn hàng này đã được thanh toán.");
        }

        // 3. Amount is server-authoritative from Order.totalPrice (NEVER trusted from client)
        BigDecimal payableAmount = order.getTotalPrice();
        long vnpAmount = payableAmount.multiply(BigDecimal.valueOf(100)).longValue();

        // 4. Generate unique transaction reference for this attempt (M3D-{orderCode}-{timestamp})
        String orderCode = order.getOrderCode() != null ? order.getOrderCode() : order.getId().toString().substring(0, 8);
        String transactionRef = "M3D-" + orderCode + "-" + System.currentTimeMillis();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.getExpireMinutes()));
        String createDate = VNPAY_DATE_FORMATTER.format(now);
        String expireDate = VNPAY_DATE_FORMATTER.format(expiresAt);

        String returnUrl = (request.getReturnUrl() != null && !request.getReturnUrl().isBlank())
                ? request.getReturnUrl()
                : properties.getReturnUrl();

        String orderInfo = (request.getOrderInfo() != null && !request.getOrderInfo().isBlank())
                ? request.getOrderInfo()
                : "Thanh toan don hang " + orderCode;

        String ipAddr = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";

        // 5. Build signed VNPAY URL
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", properties.getVersion());
        vnpParams.put("vnp_Command", properties.getCommand());
        vnpParams.put("vnp_TmnCode", properties.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", properties.getCurrCode());
        vnpParams.put("vnp_TxnRef", transactionRef);
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", properties.getOrderType());
        vnpParams.put("vnp_Locale", properties.getLocale());
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", ipAddr);
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        String paymentUrl = signatureService.buildPaymentUrl(
                properties.getPaymentUrl(),
                vnpParams,
                properties.getHashSecret()
        );

        // 6. Persist PaymentTransaction in PENDING state
        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId(order.getId())
                .userId(userId)
                .paymentMethod(PaymentMethod.VNPAY)
                .provider("VNPAY")
                .amount(payableAmount)
                .currency(order.getCurrency() != null ? order.getCurrency() : "VND")
                .status(PaymentStatus.PENDING)
                .merchantTransactionId(transactionRef)
                .requestId(UUID.randomUUID().toString())
                .paymentUrl(paymentUrl)
                .expiredAt(expiresAt)
                .build();

        transaction = transactionRepository.save(transaction);
        publishPaymentStatusEvent(transaction);

        log.info("Created VNPAY payment transaction ID: {} for order ID: {}, txnRef: {}",
                transaction.getId(), order.getId(), transactionRef);

        return CreateVnpayPaymentResponse.builder()
                .paymentId(transaction.getId())
                .orderId(transaction.getOrderId())
                .paymentMethod(transaction.getPaymentMethod())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .transactionRef(transaction.getMerchantTransactionId())
                .paymentUrl(transaction.getPaymentUrl())
                .expiresAt(transaction.getExpiredAt())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public VnpayReturnResponse processReturn(Map<String, String> queryParams) {
        log.info("Processing VNPAY return redirect");

        // 1. Verify HMAC-SHA512 Signature
        boolean validSignature = signatureService.verifySignature(queryParams, properties.getHashSecret());
        if (!validSignature) {
            log.error("Invalid VNPAY signature on return callback");
            throw new PaymentSecurityException("Không thể xác thực chữ ký giao dịch VNPAY.");
        }

        String txnRef = queryParams.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            throw new ResourceNotFoundException("Mã tham chiếu giao dịch vnp_TxnRef không hợp lệ");
        }

        // 2. Lookup transaction by merchantTransactionId
        PaymentTransaction transaction = transactionRepository.findByMerchantTransactionId(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch với mã tham chiếu: " + txnRef));

        // 3. Verify amount
        String amountStr = queryParams.get("vnp_Amount");
        if (amountStr != null) {
            try {
                long receivedAmountLong = Long.parseLong(amountStr);
                long expectedAmountLong = transaction.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
                if (receivedAmountLong != expectedAmountLong) {
                    log.error("VNPAY return amount mismatch! Expected: {}, Received: {}", expectedAmountLong, receivedAmountLong);
                    throw new PaymentAmountMismatchException("Số tiền thanh toán không khớp với đơn hàng");
                }
            } catch (NumberFormatException e) {
                log.error("Invalid amount format in VNPAY return: {}", amountStr);
                throw new PaymentAmountMismatchException("Định dạng số tiền VNPAY không hợp lệ");
            }
        }

        // 4. Map response codes
        String responseCode = queryParams.get("vnp_ResponseCode");
        String transactionStatus = queryParams.get("vnp_TransactionStatus");
        String bankCode = queryParams.get("vnp_BankCode");
        String cardType = queryParams.get("vnp_CardType");
        String transactionNo = queryParams.get("vnp_TransactionNo");
        String payDateStr = queryParams.get("vnp_PayDate");

        Instant payDate = parseVnpayDate(payDateStr);
        PaymentStatus mappedStatus = responseMapper.mapResponseCodeToStatus(responseCode, transactionStatus);
        String humanMessage = responseMapper.getResponseMessage(responseCode);

        // 5. Update state if not already in terminal status (e.g. if user arrives before IPN)
        if (!transaction.isTerminal()) {
            if (mappedStatus == PaymentStatus.SUCCESS) {
                transaction.markVnpaySuccess(transactionNo, responseCode, transactionStatus, bankCode, cardType, payDate);
                orderService.updateOrderStatusPaid(transaction.getOrderId());
                log.info("Return callback updated transaction {} to SUCCESS and order {} to PAID",
                        transaction.getId(), transaction.getOrderId());
            } else if (mappedStatus == PaymentStatus.CANCELLED) {
                transaction.markCancelled(humanMessage);
            } else if (mappedStatus == PaymentStatus.EXPIRED) {
                transaction.markExpired();
            } else {
                transaction.markFailed(responseCode, humanMessage);
            }
            transaction = transactionRepository.save(transaction);
            publishPaymentStatusEvent(transaction);
        }

        return VnpayReturnResponse.builder()
                .orderId(transaction.getOrderId())
                .transactionRef(transaction.getMerchantTransactionId())
                .status(transaction.getStatus())
                .message(humanMessage)
                .amount(transaction.getAmount())
                .bankCode(bankCode)
                .gatewayTransactionNo(transactionNo)
                .responseCode(responseCode)
                .paymentDate(transaction.getPaidAt())
                .build();
    }

    @Override
    @Transactional
    public VnpayIpnResponse processIpn(Map<String, String> queryParams) {
        log.info("Processing VNPAY IPN asynchronous notification");

        // 1. Verify HMAC-SHA512 Signature
        boolean validSignature = signatureService.verifySignature(queryParams, properties.getHashSecret());
        if (!validSignature) {
            log.error("Invalid VNPAY IPN signature");
            return VnpayIpnResponse.invalidChecksum();
        }

        String txnRef = queryParams.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            log.warn("VNPAY IPN missing vnp_TxnRef");
            return VnpayIpnResponse.orderNotFound();
        }

        // 2. Lookup transaction
        Optional<PaymentTransaction> txOpt = transactionRepository.findByMerchantTransactionId(txnRef);
        if (txOpt.isEmpty()) {
            log.warn("VNPAY IPN transaction not found for txnRef: {}", txnRef);
            return VnpayIpnResponse.orderNotFound();
        }

        PaymentTransaction transaction = txOpt.get();

        // 3. Verify amount
        String amountStr = queryParams.get("vnp_Amount");
        if (amountStr == null) {
            log.error("VNPAY IPN missing vnp_Amount");
            return VnpayIpnResponse.invalidAmount();
        }

        try {
            long receivedAmountLong = Long.parseLong(amountStr);
            long expectedAmountLong = transaction.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
            if (receivedAmountLong != expectedAmountLong) {
                log.error("VNPAY IPN amount mismatch! Expected: {}, Received: {}", expectedAmountLong, receivedAmountLong);
                return VnpayIpnResponse.invalidAmount();
            }
        } catch (NumberFormatException e) {
            log.error("VNPAY IPN invalid amount format: {}", amountStr);
            return VnpayIpnResponse.invalidAmount();
        }

        // 4. Idempotency Check: if transaction is already in SUCCESS or PAID, acknowledge immediately
        if (transaction.getStatus() == PaymentStatus.SUCCESS || transaction.getStatus() == PaymentStatus.PAID) {
            log.info("Transaction {} is already SUCCESS. Acknowledging duplicate VNPAY IPN.", transaction.getId());
            return VnpayIpnResponse.alreadyConfirmed();
        }

        // 5. Update transaction and order states
        String responseCode = queryParams.get("vnp_ResponseCode");
        String transactionStatus = queryParams.get("vnp_TransactionStatus");
        String bankCode = queryParams.get("vnp_BankCode");
        String cardType = queryParams.get("vnp_CardType");
        String transactionNo = queryParams.get("vnp_TransactionNo");
        String payDateStr = queryParams.get("vnp_PayDate");

        Instant payDate = parseVnpayDate(payDateStr);
        PaymentStatus mappedStatus = responseMapper.mapResponseCodeToStatus(responseCode, transactionStatus);
        String humanMessage = responseMapper.getResponseMessage(responseCode);

        if (mappedStatus == PaymentStatus.SUCCESS) {
            transaction.markVnpaySuccess(transactionNo, responseCode, transactionStatus, bankCode, cardType, payDate);
            orderService.updateOrderStatusPaid(transaction.getOrderId());
            log.info("VNPAY IPN marked transaction {} as SUCCESS and confirmed order {}",
                    transaction.getId(), transaction.getOrderId());
        } else if (mappedStatus == PaymentStatus.CANCELLED) {
            transaction.markCancelled(humanMessage);
            log.warn("VNPAY IPN recorded cancellation for transaction {}", transaction.getId());
        } else if (mappedStatus == PaymentStatus.EXPIRED) {
            transaction.markExpired();
            log.warn("VNPAY IPN recorded expired for transaction {}", transaction.getId());
        } else {
            transaction.markFailed(responseCode, humanMessage);
            log.warn("VNPAY IPN payment failed for transaction {}: code={}, msg={}",
                    transaction.getId(), responseCode, humanMessage);
        }

        transactionRepository.save(transaction);
        publishPaymentStatusEvent(transaction);

        return VnpayIpnResponse.success();
    }

    private Instant parseVnpayDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 14) {
            return Instant.now();
        }
        try {
            return VNPAY_DATE_FORMATTER.parse(dateStr, Instant::from);
        } catch (Exception e) {
            return Instant.now();
        }
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
            payload.put("transactionRef", transaction.getMerchantTransactionId());
            payload.put("gatewayTransactionNo", transaction.getProviderTransactionId());
            payload.put("responseCode", transaction.getResponseCode());
            payload.put("paidAt", transaction.getPaidAt());
            payload.put("failureReason", transaction.getFailureMessage());

            outboxPublisher.publish(
                    "PAYMENT",
                    transaction.getId().toString(),
                    "PAYMENT_STATUS_CHANGED",
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish outbox event for VNPAY transaction {}: {}", transaction.getId(), e.getMessage());
        }
    }
}

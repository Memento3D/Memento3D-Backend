package com.memento3d.payment.infrastructure.gateway.vnpay;

import com.memento3d.payment.application.dto.GatewayPaymentRequest;
import com.memento3d.payment.application.dto.GatewayPaymentResponse;
import com.memento3d.payment.application.dto.GatewayQueryResponse;
import com.memento3d.payment.domain.exception.PaymentGatewayException;
import com.memento3d.payment.domain.gateway.PaymentGateway;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayPaymentGateway implements PaymentGateway {

    private final VnpayProperties properties;
    private final VnpaySignatureService signatureService;

    private static final DateTimeFormatter VNPAY_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public GatewayPaymentResponse createPayment(PaymentTransaction transaction, GatewayPaymentRequest request) {
        log.info("Generating VNPAY payment URL for transaction ID: {}, merchantTxId: {}",
                transaction.getId(), transaction.getMerchantTransactionId());

        try {
            if (properties.getHashSecret() == null || properties.getHashSecret().isBlank()) {
                throw new PaymentGatewayException("VNPAY hash secret is not configured");
            }
            if (properties.getTmnCode() == null || properties.getTmnCode().isBlank()) {
                throw new PaymentGatewayException("VNPAY TMN code is not configured");
            }

            long vnpAmount = transaction.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
            Instant now = Instant.now();
            Instant expireAt = now.plus(Duration.ofMinutes(properties.getExpireMinutes()));

            String createDate = VNPAY_DATE_FORMATTER.format(now);
            String expireDate = VNPAY_DATE_FORMATTER.format(expireAt);

            String returnUrl = (request != null && request.getRedirectUrl() != null && !request.getRedirectUrl().isBlank())
                    ? request.getRedirectUrl()
                    : properties.getReturnUrl();

            String orderInfo = (request != null && request.getOrderInfo() != null && !request.getOrderInfo().isBlank())
                    ? request.getOrderInfo()
                    : "Thanh toan don hang " + transaction.getOrderId();

            String clientIp = (request != null && request.getExtraData() != null && !request.getExtraData().isBlank())
                    ? request.getExtraData()
                    : "127.0.0.1";

            Map<String, String> vnpParams = new HashMap<>();
            vnpParams.put("vnp_Version", properties.getVersion());
            vnpParams.put("vnp_Command", properties.getCommand());
            vnpParams.put("vnp_TmnCode", properties.getTmnCode());
            vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
            vnpParams.put("vnp_CurrCode", properties.getCurrCode());
            vnpParams.put("vnp_TxnRef", transaction.getMerchantTransactionId());
            vnpParams.put("vnp_OrderInfo", orderInfo);
            vnpParams.put("vnp_OrderType", properties.getOrderType());
            vnpParams.put("vnp_Locale", properties.getLocale());
            vnpParams.put("vnp_ReturnUrl", returnUrl);
            vnpParams.put("vnp_IpAddr", clientIp);
            vnpParams.put("vnp_CreateDate", createDate);
            vnpParams.put("vnp_ExpireDate", expireDate);

            String paymentUrl = signatureService.buildPaymentUrl(
                    properties.getPaymentUrl(),
                    vnpParams,
                    properties.getHashSecret()
            );

            transaction.setExpiresAt(expireAt);

            log.info("Successfully generated VNPAY URL for transaction ID={}", transaction.getId());

            return GatewayPaymentResponse.builder()
                    .success(true)
                    .paymentUrl(paymentUrl)
                    .responseCode("00")
                    .message("VNPAY payment URL created")
                    .build();

        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to build VNPAY payment URL: {}", e.getMessage(), e);
            throw new PaymentGatewayException("Failed to generate VNPAY payment URL: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayQueryResponse queryPayment(PaymentTransaction transaction) {
        log.info("Querying status for VNPAY transaction ID: {}", transaction.getId());
        return GatewayQueryResponse.builder()
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .providerTransactionId(transaction.getProviderTransactionId())
                .responseCode(transaction.getResponseCode())
                .paidAt(transaction.getPaidAt())
                .message("Queried current status from local store")
                .build();
    }
}

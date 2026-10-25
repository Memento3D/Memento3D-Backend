package com.memento3d.admin.dto;

import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Financial transaction audit record")
public class AdminPaymentTransactionResponse {

    private UUID id;
    private UUID orderId;
    private UUID userId;
    private String provider;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String merchantTransactionId;
    private String transactionReference;
    private String providerTransactionId;
    private String gatewayTransactionNo;
    private String responseCode;
    private String gatewayStatus;
    private String bankCode;
    private String cardType;
    private Instant paidAt;
    private Instant expiresAt;
    private Instant createdAt;
    private String failureMessage;

    public static AdminPaymentTransactionResponse fromEntity(PaymentTransaction tx) {
        if (tx == null) return null;
        return AdminPaymentTransactionResponse.builder()
                .id(tx.getId())
                .orderId(tx.getOrderId())
                .userId(tx.getUserId())
                .provider(tx.getProvider())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .paymentMethod(tx.getPaymentMethod())
                .status(tx.getStatus())
                .merchantTransactionId(tx.getMerchantTransactionId())
                .transactionReference(tx.getMerchantTransactionId())
                .providerTransactionId(tx.getProviderTransactionId())
                .gatewayTransactionNo(tx.getProviderTransactionId())
                .responseCode(tx.getResponseCode())
                .gatewayStatus(tx.getGatewayStatus())
                .bankCode(tx.getBankCode())
                .cardType(tx.getCardType())
                .paidAt(tx.getPaidAt())
                .expiresAt(tx.getExpiredAt())
                .createdAt(tx.getCreatedAt())
                .failureMessage(tx.getFailureMessage())
                .build();
    }
}

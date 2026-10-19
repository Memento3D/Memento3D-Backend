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
    private String providerTransactionId;
    private Instant paidAt;
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
                .providerTransactionId(tx.getProviderTransactionId())
                .paidAt(tx.getPaidAt())
                .createdAt(tx.getCreatedAt())
                .failureMessage(tx.getFailureMessage())
                .build();
    }
}

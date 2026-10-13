package com.memento3d.payment.application.dto;

import com.memento3d.payment.domain.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayQueryResponse {
    private PaymentStatus status;
    private String providerTransactionId;
    private BigDecimal amount;
    private String responseCode;
    private String message;
    private Instant paidAt;
}

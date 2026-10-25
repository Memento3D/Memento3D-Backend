package com.memento3d.payment.infrastructure.gateway.vnpay.dto;

import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response containing VNPAY payment transaction and gateway redirect URL")
public class CreateVnpayPaymentResponse {

    @Schema(description = "Payment Transaction UUID")
    private UUID paymentId;

    @Schema(description = "Order UUID")
    private UUID orderId;

    @Schema(description = "Payment Method", example = "VNPAY")
    private PaymentMethod paymentMethod;

    @Schema(description = "Payment Status", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "Payable Amount in VND", example = "550000.00")
    private BigDecimal amount;

    @Schema(description = "Currency", example = "VND")
    private String currency;

    @Schema(description = "Unique Merchant Transaction Reference", example = "M3D-ORD-20261025-1728830123")
    private String transactionRef;

    @Schema(description = "Signed VNPAY sandbox checkout URL to redirect customer to")
    private String paymentUrl;

    @Schema(description = "Payment transaction expiration timestamp")
    private Instant expiresAt;

    @Schema(description = "Payment transaction creation timestamp")
    private Instant createdAt;
}

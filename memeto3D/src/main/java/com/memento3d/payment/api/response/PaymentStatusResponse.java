package com.memento3d.payment.api.response;

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
@Schema(description = "Payment status details response")
public class PaymentStatusResponse {

    @Schema(description = "Payment transaction UUID", example = "3c89b52a-a910-4f8f-94d6-dbb6448a6783")
    private UUID paymentId;

    @Schema(description = "Associated order UUID", example = "a0fa2625-8fd6-451f-a253-387153af2ff0")
    private UUID orderId;

    @Schema(description = "Payment method", example = "MOMO")
    private PaymentMethod paymentMethod;

    @Schema(description = "Payment status", example = "SUCCESS")
    private PaymentStatus status;

    @Schema(description = "Amount", example = "50000.00")
    private BigDecimal amount;

    @Schema(description = "Currency", example = "VND")
    private String currency;

    @Schema(description = "Merchant unique transaction reference", example = "MOMOTX_1728829100000_123456")
    private String merchantTransactionId;

    @Schema(description = "Provider transaction ID (from MoMo/ZaloPay)", example = "3128945678")
    private String providerTransactionId;

    @Schema(description = "Failure error code if payment failed")
    private String failureCode;

    @Schema(description = "Failure detail message if payment failed")
    private String failureMessage;

    @Schema(description = "Timestamp when the payment was confirmed successful")
    private Instant paidAt;

    @Schema(description = "Payment creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}

package com.memento3d.payment.infrastructure.gateway.vnpay.dto;

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
@Schema(description = "Result of VNPAY return redirect verification")
public class VnpayReturnResponse {

    @Schema(description = "Order UUID")
    private UUID orderId;

    @Schema(description = "Unique Merchant Transaction Reference", example = "M3D-ORD-20261025-1728830123")
    private String transactionRef;

    @Schema(description = "Payment Status: SUCCESS, FAILED, CANCELLED, EXPIRED, PROCESSING")
    private PaymentStatus status;

    @Schema(description = "Vietnamese message describing payment status")
    private String message;

    @Schema(description = "Payment Amount in VND", example = "550000.00")
    private BigDecimal amount;

    @Schema(description = "Bank code used on VNPAY", example = "NCB")
    private String bankCode;

    @Schema(description = "VNPAY Transaction Number", example = "14589230")
    private String gatewayTransactionNo;

    @Schema(description = "VNPAY Response Code", example = "00")
    private String responseCode;

    @Schema(description = "Payment timestamp")
    private Instant paymentDate;
}

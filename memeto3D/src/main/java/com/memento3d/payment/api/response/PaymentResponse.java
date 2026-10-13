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
@Schema(description = "Payment creation response payload")
public class PaymentResponse {

    @Schema(description = "Payment transaction UUID", example = "3c89b52a-a910-4f8f-94d6-dbb6448a6783")
    private UUID paymentId;

    @Schema(description = "Associated order UUID", example = "a0fa2625-8fd6-451f-a253-387153af2ff0")
    private UUID orderId;

    @Schema(description = "Payment method", example = "MOMO")
    private PaymentMethod paymentMethod;

    @Schema(description = "Current payment status", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "Payable amount", example = "50000.00")
    private BigDecimal amount;

    @Schema(description = "Currency", example = "VND")
    private String currency;

    @Schema(description = "URL to redirect the customer to complete payment", example = "https://test-payment.momo.vn/v2/gateway/pay?...")
    private String paymentUrl;

    @Schema(description = "QR code URL if supplied by the provider")
    private String qrCodeUrl;

    @Schema(description = "Deep link for mobile wallet app if supplied")
    private String deeplink;

    @Schema(description = "Merchant unique transaction reference", example = "MOMOTX_1728829100000_123456")
    private String merchantTransactionId;

    @Schema(description = "Payment initiation timestamp")
    private Instant createdAt;
}

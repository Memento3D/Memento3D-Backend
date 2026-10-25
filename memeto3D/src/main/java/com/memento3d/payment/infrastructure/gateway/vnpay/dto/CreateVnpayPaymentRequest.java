package com.memento3d.payment.infrastructure.gateway.vnpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to initiate VNPAY sandbox payment for an existing unpaid order")
public class CreateVnpayPaymentRequest {

    @NotNull(message = "orderId is required")
    @Schema(description = "UUID of the Order to pay for", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID orderId;

    @Schema(description = "Optional custom return URL override", example = "http://localhost:5173/payment/vnpay/return")
    private String returnUrl;

    @Schema(description = "Optional custom order description", example = "Thanh toan don hang Memento3D")
    private String orderInfo;
}

package com.memento3d.payment.api.request;

import com.memento3d.payment.domain.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payment creation request payload")
public class CreatePaymentRequest {

    @NotNull(message = "orderId is required")
    @Schema(description = "Order UUID to be paid", example = "a0fa2625-8fd6-451f-a253-387153af2ff0")
    private UUID orderId;

    @NotNull(message = "paymentMethod is required")
    @Schema(description = "Payment method: MOMO or ZALOPAY", example = "MOMO")
    private PaymentMethod paymentMethod;

    @Schema(description = "Optional frontend redirect URL after payment completion", example = "http://localhost:3000/orders/123/result")
    private String redirectUrl;

    @Schema(description = "Optional note or information regarding the order", example = "Payment for 3D Print Order")
    private String orderInfo;
}

package com.memento3d.orders.api.request;

import com.memento3d.payment.domain.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload to create a new order (from cart checkout or scan model)")
public class CreateOrderRequest {

    @Schema(description = "Optional scan session UUID for bespoke 3D scan order", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID scanId;

    @Schema(description = "Optional total price (server will recalculate based on active cart and product prices if empty)", example = "250000.00")
    private BigDecimal totalPrice;

    @Schema(description = "Currency code (default VND)", example = "VND")
    @Builder.Default
    private String currency = "VND";

    @NotBlank(message = "Recipient name is required")
    @Size(max = 255, message = "Recipient name must not exceed 255 characters")
    @Schema(description = "Full name of delivery recipient", example = "Nguyen Van A")
    private String recipientName;

    @NotBlank(message = "Shipping address is required")
    @Schema(description = "Complete shipping delivery address", example = "123 Nguyen Hue, Ben Nghe, District 1, HCMC")
    private String shippingAddress;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9+()\\-\\s]{8,20}$", message = "Invalid phone number format")
    @Schema(description = "Contact phone number for delivery", example = "0901234567")
    private String phone;

    @Schema(description = "Selected payment method: MOMO, ZALOPAY, or CASH", example = "CASH")
    private PaymentMethod paymentMethod;

    @Schema(description = "Order note or delivery instructions", example = "Deliver in the afternoon")
    private String note;
}

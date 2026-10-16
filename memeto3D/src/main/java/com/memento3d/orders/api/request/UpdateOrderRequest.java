package com.memento3d.orders.api.request;

import com.memento3d.orders.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload to update shipping details or order status")
public class UpdateOrderRequest {

    @Size(max = 255, message = "Recipient name must not exceed 255 characters")
    @Schema(description = "Updated delivery recipient name", example = "Nguyen Van B")
    private String recipientName;

    @Schema(description = "Updated shipping delivery address", example = "456 Le Loi, District 1, HCMC")
    private String shippingAddress;

    @Pattern(regexp = "^[0-9+()\\-\\s]{8,20}$", message = "Invalid phone number format")
    @Schema(description = "Updated delivery contact phone number", example = "0987654321")
    private String phone;

    @Schema(description = "Order status (Admin / Operator only)", example = "PRINTING")
    private OrderStatus status;
}

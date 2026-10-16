package com.memento3d.orders.api.response;

import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
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
@Schema(description = "3D print order details response")
public class OrderResponse {

    @Schema(description = "Unique order identifier", example = "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e")
    private UUID id;

    @Schema(description = "User identifier who created the order", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID userId;

    @Schema(description = "Scan session identifier associated with the order", example = "c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f")
    private UUID scanId;

    @Schema(description = "Total payable price", example = "250000.00")
    private BigDecimal totalPrice;

    @Schema(description = "Currency code", example = "VND")
    private String currency;

    @Schema(description = "Current lifecycle status of the order", example = "PENDING_PAYMENT")
    private OrderStatus status;

    @Schema(description = "Delivery recipient name", example = "Nguyen Van A")
    private String recipientName;

    @Schema(description = "Delivery shipping address", example = "123 Nguyen Hue, Ben Nghe, District 1, HCMC")
    private String shippingAddress;

    @Schema(description = "Contact phone number", example = "0901234567")
    private String phone;

    @Schema(description = "Timestamp when order was placed")
    private Instant createdAt;

    @Schema(description = "Timestamp of last modification")
    private Instant updatedAt;

    public static OrderResponse fromEntity(Order order) {
        if (order == null) return null;
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .scanId(order.getScanId())
                .totalPrice(order.getTotalPrice())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .recipientName(order.getRecipientName())
                .shippingAddress(order.getShippingAddress())
                .phone(order.getPhone())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}

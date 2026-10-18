package com.memento3d.orders.api.response;

import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Schema(description = "Human readable order tracking code", example = "ORD-20261018-ABCD")
    private String orderCode;

    @Schema(description = "User identifier who created the order", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID userId;

    @Schema(description = "Scan session identifier associated with the order", example = "c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f")
    private UUID scanId;

    @Schema(description = "Subtotal before discount & shipping", example = "250000.00")
    private BigDecimal subtotal;

    @Schema(description = "Discount amount", example = "0.00")
    private BigDecimal discountAmount;

    @Schema(description = "Shipping delivery fee", example = "0.00")
    private BigDecimal shippingFee;

    @Schema(description = "Total payable price", example = "250000.00")
    private BigDecimal totalPrice;

    @Schema(description = "Currency code", example = "VND")
    private String currency;

    @Schema(description = "Current lifecycle status of the order", example = "CONFIRMED")
    private OrderStatus status;

    @Schema(description = "Payment method selected", example = "MOMO")
    private PaymentMethod paymentMethod;

    @Schema(description = "Payment processing status", example = "PAID")
    private PaymentStatus paymentStatus;

    @Schema(description = "Delivery recipient name", example = "Nguyen Van A")
    private String recipientName;

    @Schema(description = "Delivery shipping address", example = "123 Nguyen Hue, Ben Nghe, District 1, HCMC")
    private String shippingAddress;

    @Schema(description = "Contact phone number", example = "0901234567")
    private String phone;

    @Schema(description = "Order note from customer", example = "Please deliver during office hours")
    private String note;

    @Builder.Default
    @Schema(description = "List of items in the order")
    private List<OrderItemResponse> items = new ArrayList<>();

    @Schema(description = "Timestamp when order was placed")
    private Instant createdAt;

    @Schema(description = "Timestamp of last modification")
    private Instant updatedAt;

    public static OrderResponse fromEntity(Order order) {
        if (order == null) return null;

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            itemResponses = order.getItems().stream()
                    .map(OrderItemResponse::fromEntity)
                    .toList();
        }

        String code = order.getOrderCode();
        if (code == null && order.getId() != null) {
            code = "ORD-" + order.getId().toString().substring(0, 8).toUpperCase();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(code)
                .userId(order.getUserId())
                .scanId(order.getScanId())
                .subtotal(order.getSubtotal() != null ? order.getSubtotal() : order.getTotalPrice())
                .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .shippingFee(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                .totalPrice(order.getTotalPrice())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus() : PaymentStatus.PENDING)
                .recipientName(order.getRecipientName())
                .shippingAddress(order.getShippingAddress())
                .phone(order.getPhone())
                .note(order.getNote())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}

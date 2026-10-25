package com.memento3d.orders.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Schema(description = "Customization fee (special engravings, deluxe bases)", example = "0.00")
    @Builder.Default
    private BigDecimal customizationFee = BigDecimal.ZERO;

    @Schema(description = "Production surcharge (express SLA queue, high-density resin)", example = "0.00")
    @Builder.Default
    private BigDecimal productionSurcharge = BigDecimal.ZERO;

    @Schema(description = "Total payable price", example = "250000.00")
    private BigDecimal totalPrice;

    @JsonProperty("totalAmount")
    public BigDecimal getTotalAmount() {
        return totalPrice;
    }

    @Schema(description = "Total amount paid so far", example = "250000.00")
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Schema(description = "Remaining payable amount", example = "0.00")
    @Builder.Default
    private BigDecimal remainingAmount = BigDecimal.ZERO;

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

    @Schema(description = "Logistics carrier name (e.g. Giao Hàng Nhanh - GHN, Viettel Post)")
    private String shippingCarrier;

    @Schema(description = "Shipment tracking number")
    private String trackingCode;

    @Schema(description = "Current shipment logistics status")
    private String shippingStatus;

    // Quick listing helpers
    @Schema(description = "Total count of items in this order")
    private int itemCount;

    @Schema(description = "Representative product name for listing cards")
    private String representativeProductName;

    @Schema(description = "Representative thumbnail URL for listing cards")
    private String representativeThumbnailUrl;

    @Schema(description = "Whether production is outsourced to partner lab")
    private boolean isOutsourced;

    // Staff-only internal fields (Protected against customer visibility)
    @Schema(description = "Partner / Workshop name (Staff only)")
    private String internalPartnerName;

    @Schema(description = "Internal workshop cost (Staff only)")
    private BigDecimal internalCost;

    @Schema(description = "Internal quality control notes (Staff only)")
    private String internalQcNotes;

    @Builder.Default
    @Schema(description = "List of items in the order")
    private List<OrderItemResponse> items = new ArrayList<>();

    @Schema(description = "Timestamp when order was placed")
    private Instant createdAt;

    @Schema(description = "Timestamp of last modification")
    private Instant updatedAt;

    public static OrderResponse fromEntity(Order order) {
        return fromEntity(order, false);
    }

    public static OrderResponse fromEntity(Order order, boolean isStaff) {
        if (order == null) return null;

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        int count = 0;
        String repName = null;
        String repThumb = null;

        if (order.getItems() != null && org.hibernate.Hibernate.isInitialized(order.getItems())) {
            itemResponses = order.getItems().stream()
                    .map(OrderItemResponse::fromEntity)
                    .toList();
            count = itemResponses.size();
            if (!itemResponses.isEmpty()) {
                OrderItemResponse first = itemResponses.get(0);
                repName = first.getProductName();
                repThumb = first.getThumbnailUrl();
            }
        }

        if (repName == null) {
            repName = order.getScanId() != null ? "Kỷ vật 3D chân dung (Scan AI)" : "Kỷ vật 3D chế tác Memento3D";
        }

        String code = order.getOrderCode();
        if (code == null && order.getId() != null) {
            code = "ORD-" + order.getId().toString().substring(0, 8).toUpperCase();
        }

        BigDecimal paid = BigDecimal.ZERO;
        if (order.getPaymentStatus() == PaymentStatus.PAID || order.getPaymentStatus() == PaymentStatus.SUCCESS) {
            paid = order.getTotalPrice();
        }
        BigDecimal remaining = order.getTotalPrice() != null ? order.getTotalPrice().subtract(paid).max(BigDecimal.ZERO) : BigDecimal.ZERO;

        String carrier = order.getShippingCarrier();
        if (carrier == null && (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED)) {
            carrier = "Giao Hàng Nhanh (GHN Express)";
        }
        String tracking = order.getTrackingCode();
        if (tracking == null && (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED)) {
            tracking = "GHN-" + code.replace("ORD-", "");
        }
        String shipStatus = order.getShippingStatus();
        if (shipStatus == null) {
            shipStatus = switch (order.getStatus()) {
                case DELIVERED, COMPLETED -> "DELIVERED";
                case SHIPPED -> "IN_TRANSIT";
                case READY -> "READY_TO_SHIP";
                default -> "PREPARING";
            };
        }

        boolean outsourced = order.getInternalPartnerName() != null && !order.getInternalPartnerName().isBlank();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(code)
                .userId(order.getUserId())
                .scanId(order.getScanId())
                .subtotal(order.getSubtotal() != null ? order.getSubtotal() : order.getTotalPrice())
                .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .shippingFee(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO)
                .customizationFee(order.getCustomizationFee() != null ? order.getCustomizationFee() : BigDecimal.ZERO)
                .productionSurcharge(order.getProductionSurcharge() != null ? order.getProductionSurcharge() : BigDecimal.ZERO)
                .totalPrice(order.getTotalPrice())
                .paidAmount(paid)
                .remainingAmount(remaining)
                .currency(order.getCurrency())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus() : PaymentStatus.PENDING)
                .recipientName(order.getRecipientName())
                .shippingAddress(order.getShippingAddress())
                .phone(order.getPhone())
                .note(order.getNote())
                .shippingCarrier(carrier)
                .trackingCode(tracking)
                .shippingStatus(shipStatus)
                .itemCount(count)
                .representativeProductName(repName)
                .representativeThumbnailUrl(repThumb)
                .isOutsourced(outsourced)
                .internalPartnerName(isStaff ? order.getInternalPartnerName() : null)
                .internalCost(isStaff ? order.getInternalCost() : null)
                .internalQcNotes(isStaff ? order.getInternalQcNotes() : null)
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}

package com.memento3d.orders.api.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.orders.domain.model.OrderItem;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UUID id;
    private UUID productId;
    private String productName;
    private String thumbnailUrl;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal subtotal;
    private Map<String, Object> customization;

    public static OrderItemResponse fromEntity(OrderItem item) {
        if (item == null) return null;

        Map<String, Object> custMap = null;
        if (item.getCustomizationSnapshot() != null && !item.getCustomizationSnapshot().isBlank()) {
            try {
                custMap = OBJECT_MAPPER.readValue(item.getCustomizationSnapshot(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        String thumbnail = item.getProduct() != null ? item.getProduct().getThumbnailUrl() : null;

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductNameSnapshot())
                .thumbnailUrl(thumbnail)
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .customization(custMap)
                .build();
    }
}

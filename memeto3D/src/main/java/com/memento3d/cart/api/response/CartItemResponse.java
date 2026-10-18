package com.memento3d.cart.api.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.cart.domain.model.CartItem;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductAvailability;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UUID id;
    private UUID productId;
    private String productName;
    private String productSlug;
    private String thumbnailUrl;
    private String categoryName;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal subtotal;
    private Map<String, Object> customization;
    private boolean active;
    private ProductAvailability availability;

    public static CartItemResponse fromEntity(CartItem item) {
        Product p = item.getProduct();
        Map<String, Object> custMap = null;
        if (item.getCustomizationData() != null && !item.getCustomizationData().isBlank()) {
            try {
                custMap = OBJECT_MAPPER.readValue(item.getCustomizationData(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        BigDecimal unitPrice = p != null ? p.getPrice() : item.getUnitPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(p != null ? p.getId() : null)
                .productName(p != null ? p.getName() : "Unknown Product")
                .productSlug(p != null ? p.getSlug() : null)
                .thumbnailUrl(p != null ? p.getThumbnailUrl() : null)
                .categoryName(p != null && p.getCategory() != null ? p.getCategory().getName() : null)
                .unitPrice(unitPrice)
                .quantity(item.getQuantity())
                .subtotal(subtotal)
                .customization(custMap)
                .active(p != null && p.isActive())
                .availability(p != null ? p.getAvailability() : ProductAvailability.OUT_OF_STOCK)
                .build();
    }
}

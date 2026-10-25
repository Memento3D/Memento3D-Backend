package com.memento3d.orders.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.orders.domain.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "3D Print Order Item specification response")
public class OrderItemResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private UUID id;
    private UUID productId;

    @Schema(description = "Product display name")
    private String productName;

    @JsonProperty("productNameSnapshot")
    public String getProductNameSnapshot() {
        return productName;
    }

    @Schema(description = "Thumbnail image URL")
    private String thumbnailUrl;

    @JsonProperty("thumbnailSnapshot")
    public String getThumbnailSnapshot() {
        return thumbnailUrl;
    }

    @Schema(description = "Stock Keeping Unit / Identifier")
    private String sku;

    @Schema(description = "Product category / tier (e.g. Mini Keepsake, Standard, Premium)")
    private String categoryName;

    @Schema(description = "Unit price at purchase time")
    private BigDecimal unitPrice;

    @Schema(description = "Purchased quantity")
    private int quantity;

    @Schema(description = "Subtotal for this line item")
    private BigDecimal subtotal;

    @Schema(description = "Material type (e.g. Resin, PLA, PETG)")
    private String material;

    @Schema(description = "3D Printing Technology (e.g. SLA, FDM, SLS)")
    private String technology;

    @Schema(description = "Product dimensions / scale (e.g. 12-15cm, 5x5x8 cm)")
    private String dimensions;

    @Schema(description = "Color specification")
    private String color;

    @Schema(description = "Surface finish type (e.g. Phủ bóng UV, Sơn màu thủ công)")
    private String finish;

    @Schema(description = "Display stand / base (e.g. Đế gỗ sồi tự nhiên, Đế pha lê)")
    private String base;

    @Schema(description = "Gift box & accessories included")
    private String accessories;

    @Schema(description = "Custom personalized text / engraving")
    private String personalization;

    @Schema(description = "Actual material used in production (if assigned)")
    private String actualMaterial;

    @Schema(description = "Current item production status")
    private String productionStatus;

    @Schema(description = "Raw customization key-value map")
    private Map<String, Object> customization;

    @JsonProperty("customizationSnapshot")
    public Map<String, Object> getCustomizationSnapshot() {
        return customization;
    }

    public static OrderItemResponse fromEntity(OrderItem item) {
        if (item == null) return null;

        Map<String, Object> custMap = null;
        if (item.getCustomizationSnapshot() != null && !item.getCustomizationSnapshot().isBlank()) {
            try {
                custMap = OBJECT_MAPPER.readValue(item.getCustomizationSnapshot(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        String thumbnail = null;
        String category = item.getCategoryNameSnapshot();
        String sku = item.getSkuSnapshot();

        if (item.getProduct() != null) {
            thumbnail = item.getProduct().getThumbnailUrl();
            if (category == null && item.getProduct().getCategory() != null) {
                category = item.getProduct().getCategory().getName();
            }
            if (sku == null) {
                sku = item.getProduct().getSlug() != null ? "SKU-" + item.getProduct().getSlug().toUpperCase() : null;
            }
        }

        // Material extraction with fallback to "Chưa xác định"
        String material = item.getMaterialSnapshot();
        if (material == null && custMap != null) {
            Object matObj = custMap.get("material");
            if (matObj != null) material = matObj.toString();
        }
        if (material == null || material.isBlank()) {
            material = "Chưa xác định";
        }

        // Technology inference or extraction
        String tech = item.getTechnologySnapshot();
        if (tech == null && custMap != null) {
            Object techObj = custMap.get("technology");
            if (techObj != null) tech = techObj.toString();
        }
        if (tech == null || tech.isBlank()) {
            if (material.toLowerCase().contains("resin") || material.toLowerCase().contains("photopolymer")) {
                tech = "SLA (Stereolithography)";
            } else if (material.toLowerCase().contains("pla") || material.toLowerCase().contains("petg") || material.toLowerCase().contains("abs")) {
                tech = "FDM (Fused Deposition Modeling)";
            } else {
                tech = "Chưa xác định";
            }
        }

        // Dimensions extraction
        String dimensions = item.getDimensionsSnapshot();
        if (dimensions == null && custMap != null) {
            Object dimObj = custMap.get("size");
            if (dimObj == null) dimObj = custMap.get("dimensions");
            if (dimObj != null) dimensions = dimObj.toString();
        }
        if (dimensions == null || dimensions.isBlank()) {
            dimensions = "Chưa xác định";
        }

        // Color extraction
        String color = item.getColorSnapshot();
        if (color == null && custMap != null) {
            Object colObj = custMap.get("color");
            if (colObj != null) color = colObj.toString();
        }

        // Finish extraction
        String finish = item.getFinishSnapshot();
        if (finish == null && custMap != null) {
            Object finObj = custMap.get("finish");
            if (finObj != null) finish = finObj.toString();
        }
        if (finish == null || finish.isBlank()) {
            finish = "Phủ bóng bảo vệ & Chống trầy UV";
        }

        // Base extraction
        String base = item.getBaseSnapshot();
        if (base == null && custMap != null) {
            Object baseObj = custMap.get("base");
            if (baseObj != null) base = baseObj.toString();
        }

        // Personalization / engraving
        String personalization = null;
        if (custMap != null) {
            Object engObj = custMap.get("engraving");
            if (engObj == null) engObj = custMap.get("personalization");
            if (engObj == null) engObj = custMap.get("note");
            if (engObj != null) personalization = engObj.toString();
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductNameSnapshot())
                .thumbnailUrl(thumbnail)
                .sku(sku)
                .categoryName(category != null ? category : "Kỷ vật 3D")
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .material(material)
                .technology(tech)
                .dimensions(dimensions)
                .color(color != null ? color : "Tự nhiên")
                .finish(finish)
                .base(base != null ? base : "Tiêu chuẩn")
                .personalization(personalization)
                .actualMaterial(material)
                .productionStatus("QUEUED")
                .customization(custMap)
                .build();
    }
}

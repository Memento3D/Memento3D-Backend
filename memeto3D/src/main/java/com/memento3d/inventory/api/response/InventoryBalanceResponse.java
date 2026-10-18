package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.InventoryBalance;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inventory stock balance for a material at a specific warehouse")
public class InventoryBalanceResponse {

    private UUID id;
    private UUID warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private UUID materialId;
    private String materialSku;
    private String materialName;
    private String materialColor;
    private String materialType;
    private BigDecimal onHandGram;
    private BigDecimal reservedGram;
    private BigDecimal availableGram;
    private Instant updatedAt;

    public static InventoryBalanceResponse fromEntity(InventoryBalance entity) {
        if (entity == null) return null;
        return InventoryBalanceResponse.builder()
                .id(entity.getId())
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .warehouseCode(entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null)
                .warehouseName(entity.getWarehouse() != null ? entity.getWarehouse().getName() : null)
                .materialId(entity.getMaterial() != null ? entity.getMaterial().getId() : null)
                .materialSku(entity.getMaterial() != null ? entity.getMaterial().getSku() : null)
                .materialName(entity.getMaterial() != null ? entity.getMaterial().getName() : null)
                .materialColor(entity.getMaterial() != null ? entity.getMaterial().getColor() : null)
                .materialType(entity.getMaterial() != null && entity.getMaterial().getMaterialType() != null 
                        ? entity.getMaterial().getMaterialType().name() : null)
                .onHandGram(entity.getOnHandGram())
                .reservedGram(entity.getReservedGram())
                .availableGram(entity.getAvailableGram())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

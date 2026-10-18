package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.StockReceipt;
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
@Schema(description = "Goods receipt transaction details")
public class StockReceiptResponse {

    private UUID id;
    private String receiptNumber;
    private UUID warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private UUID materialId;
    private String materialSku;
    private String materialName;
    private BigDecimal quantityGram;
    private BigDecimal unitCost;
    private String supplier;
    private String batchNumber;
    private String idempotencyKey;
    private String note;
    private UUID receivedBy;
    private Instant createdAt;

    public static StockReceiptResponse fromEntity(StockReceipt entity) {
        if (entity == null) return null;
        return StockReceiptResponse.builder()
                .id(entity.getId())
                .receiptNumber(entity.getReceiptNumber())
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .warehouseCode(entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null)
                .warehouseName(entity.getWarehouse() != null ? entity.getWarehouse().getName() : null)
                .materialId(entity.getMaterial() != null ? entity.getMaterial().getId() : null)
                .materialSku(entity.getMaterial() != null ? entity.getMaterial().getSku() : null)
                .materialName(entity.getMaterial() != null ? entity.getMaterial().getName() : null)
                .quantityGram(entity.getQuantityGram())
                .unitCost(entity.getUnitCost())
                .supplier(entity.getSupplier())
                .batchNumber(entity.getBatchNumber())
                .idempotencyKey(entity.getIdempotencyKey())
                .note(entity.getNote())
                .receivedBy(entity.getReceivedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

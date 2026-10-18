package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.InventoryTransaction;
import com.memento3d.inventory.domain.model.InventoryTransactionType;
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
@Schema(description = "Immutable inventory transaction audit entry")
public class InventoryTransactionResponse {

    private UUID id;
    private InventoryTransactionType transactionType;
    private UUID warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private UUID materialId;
    private String materialSku;
    private String materialName;
    private BigDecimal deltaOnHandGram;
    private BigDecimal deltaReservedGram;
    private BigDecimal beforeOnHandGram;
    private BigDecimal afterOnHandGram;
    private BigDecimal beforeReservedGram;
    private BigDecimal afterReservedGram;
    private UUID printJobId;
    private String referenceId;
    private String idempotencyKey;
    private UUID performedBy;
    private Instant createdAt;

    public static InventoryTransactionResponse fromEntity(InventoryTransaction entity) {
        if (entity == null) return null;
        return InventoryTransactionResponse.builder()
                .id(entity.getId())
                .transactionType(entity.getTransactionType())
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .warehouseCode(entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null)
                .warehouseName(entity.getWarehouse() != null ? entity.getWarehouse().getName() : null)
                .materialId(entity.getMaterial() != null ? entity.getMaterial().getId() : null)
                .materialSku(entity.getMaterial() != null ? entity.getMaterial().getSku() : null)
                .materialName(entity.getMaterial() != null ? entity.getMaterial().getName() : null)
                .deltaOnHandGram(entity.getDeltaOnHandGram())
                .deltaReservedGram(entity.getDeltaReservedGram())
                .beforeOnHandGram(entity.getBeforeOnHandGram())
                .afterOnHandGram(entity.getAfterOnHandGram())
                .beforeReservedGram(entity.getBeforeReservedGram())
                .afterReservedGram(entity.getAfterReservedGram())
                .printJobId(entity.getPrintJobId())
                .referenceId(entity.getReferenceId())
                .idempotencyKey(entity.getIdempotencyKey())
                .performedBy(entity.getPerformedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

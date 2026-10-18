package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.StockIssue;
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
@Schema(description = "Stock issue record details")
public class StockIssueResponse {

    private UUID id;
    private String issueNumber;
    private UUID warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private UUID materialId;
    private String materialSku;
    private String materialName;
    private BigDecimal quantityGram;
    private String reason;
    private String idempotencyKey;
    private String note;
    private UUID issuedBy;
    private Instant createdAt;

    public static StockIssueResponse fromEntity(StockIssue entity) {
        if (entity == null) return null;
        return StockIssueResponse.builder()
                .id(entity.getId())
                .issueNumber(entity.getIssueNumber())
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .warehouseCode(entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null)
                .warehouseName(entity.getWarehouse() != null ? entity.getWarehouse().getName() : null)
                .materialId(entity.getMaterial() != null ? entity.getMaterial().getId() : null)
                .materialSku(entity.getMaterial() != null ? entity.getMaterial().getSku() : null)
                .materialName(entity.getMaterial() != null ? entity.getMaterial().getName() : null)
                .quantityGram(entity.getQuantityGram())
                .reason(entity.getReason())
                .idempotencyKey(entity.getIdempotencyKey())
                .note(entity.getNote())
                .issuedBy(entity.getIssuedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

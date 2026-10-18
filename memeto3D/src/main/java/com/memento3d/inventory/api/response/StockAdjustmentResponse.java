package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.StockAdjustmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentResponse {
    private UUID id;
    private String adjustmentCode;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseCode;
    private UUID materialId;
    private String materialName;
    private String materialSku;
    private BigDecimal currentQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal difference;
    private String reason;
    private String evidence;
    private StockAdjustmentStatus status;
    private UUID requestedBy;
    private UUID approvedBy;
    private Instant appliedAt;
    private Instant createdAt;
    private Instant updatedAt;
}

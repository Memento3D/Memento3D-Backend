package com.memento3d.printjob.api.response;

import com.memento3d.printjob.domain.model.PrintJobConsumption;
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
@Schema(description = "Print Job material consumption & reconciliation details")
public class PrintJobConsumptionResponse {

    private UUID id;
    private UUID printJobId;
    private UUID warehouseId;
    private String warehouseCode;
    private UUID materialId;
    private String materialSku;
    private String materialName;
    private BigDecimal issuedGram;
    private BigDecimal usedGram;
    private BigDecimal wasteGram;
    private BigDecimal returnedGram;
    private boolean reconciled;
    private Instant reconciledAt;
    private UUID reconciledBy;
    private String note;
    private Instant createdAt;

    public static PrintJobConsumptionResponse fromEntity(PrintJobConsumption entity) {
        if (entity == null) return null;
        return PrintJobConsumptionResponse.builder()
                .id(entity.getId())
                .printJobId(entity.getPrintJob() != null ? entity.getPrintJob().getId() : null)
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .warehouseCode(entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null)
                .materialId(entity.getMaterial() != null ? entity.getMaterial().getId() : null)
                .materialSku(entity.getMaterial() != null ? entity.getMaterial().getSku() : null)
                .materialName(entity.getMaterial() != null ? entity.getMaterial().getName() : null)
                .issuedGram(entity.getIssuedGram())
                .usedGram(entity.getUsedGram())
                .wasteGram(entity.getWasteGram())
                .returnedGram(entity.getReturnedGram())
                .reconciled(entity.isReconciled())
                .reconciledAt(entity.getReconciledAt())
                .reconciledBy(entity.getReconciledBy())
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

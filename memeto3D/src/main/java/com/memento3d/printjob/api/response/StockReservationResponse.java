package com.memento3d.printjob.api.response;

import com.memento3d.inventory.domain.model.StockReservation;
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
@Schema(description = "Print Job stock reservation details")
public class StockReservationResponse {

    private UUID id;
    private UUID printJobId;
    private UUID warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private UUID materialId;
    private String materialSku;
    private String materialName;
    private BigDecimal reservedGram;
    private String status;
    private Instant createdAt;

    public static StockReservationResponse fromEntity(StockReservation entity) {
        if (entity == null) return null;
        return StockReservationResponse.builder()
                .id(entity.getId())
                .printJobId(entity.getPrintJob() != null ? entity.getPrintJob().getId() : null)
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .warehouseCode(entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null)
                .warehouseName(entity.getWarehouse() != null ? entity.getWarehouse().getName() : null)
                .materialId(entity.getMaterial() != null ? entity.getMaterial().getId() : null)
                .materialSku(entity.getMaterial() != null ? entity.getMaterial().getSku() : null)
                .materialName(entity.getMaterial() != null ? entity.getMaterial().getName() : null)
                .reservedGram(entity.getReservedGram())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

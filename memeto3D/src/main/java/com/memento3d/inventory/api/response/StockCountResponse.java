package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.StockCountStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountResponse {
    private UUID id;
    private String countCode;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseCode;
    private StockCountStatus status;
    private String note;
    private List<StockCountItemResponse> items;
    private Instant startedAt;
    private Instant completedAt;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockCountItemResponse {
        private UUID id;
        private UUID materialId;
        private String materialName;
        private String materialSku;
        private BigDecimal systemQuantity;
        private BigDecimal countedQuantity;
        private BigDecimal variance;
        private String note;
    }
}

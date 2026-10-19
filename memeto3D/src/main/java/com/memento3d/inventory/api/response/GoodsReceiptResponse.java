package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.GoodsReceiptStatus;
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
public class GoodsReceiptResponse {
    private UUID id;
    private String receiptCode;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseCode;
    private String supplier;
    private GoodsReceiptStatus status;
    private String note;
    private Integer itemCount;
    private List<GoodsReceiptItemResponse> items;
    private UUID createdBy;
    private UUID approvedBy;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GoodsReceiptItemResponse {
        private UUID id;
        private UUID materialId;
        private String materialName;
        private String materialSku;
        private BigDecimal quantity;
        private BigDecimal unitCost;
        private String batchNumber;
        private String note;
    }
}

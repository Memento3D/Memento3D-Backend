package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.WarehouseTransferStatus;
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
public class WarehouseTransferResponse {
    private UUID id;
    private String transferCode;
    private UUID sourceWarehouseId;
    private String sourceWarehouseName;
    private String sourceWarehouseCode;
    private UUID destinationWarehouseId;
    private String destinationWarehouseName;
    private String destinationWarehouseCode;
    private WarehouseTransferStatus status;
    private String note;
    private Integer itemCount;
    private List<TransferItemResponse> items;
    private UUID createdBy;
    private UUID approvedBy;
    private Instant shippedAt;
    private Instant receivedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferItemResponse {
        private UUID id;
        private UUID materialId;
        private String materialName;
        private String materialSku;
        private BigDecimal quantity;
        private String note;
    }
}

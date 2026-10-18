package com.memento3d.inventory.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGoodsReceiptRequest {

    @NotNull(message = "Warehouse ID is mandatory")
    private UUID warehouseId;

    private String supplier;

    private String note;

    @NotEmpty(message = "Goods receipt must contain at least one item")
    @Valid
    private List<GoodsReceiptItemRequest> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GoodsReceiptItemRequest {
        @NotNull(message = "Material ID is mandatory")
        private UUID materialId;

        @NotNull(message = "Quantity is mandatory")
        private BigDecimal quantity;

        private BigDecimal unitCost;

        private String batchNumber;

        private String note;
    }
}

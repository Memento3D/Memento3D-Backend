package com.memento3d.inventory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStockAdjustmentRequest {

    @NotNull(message = "Warehouse ID is mandatory")
    private UUID warehouseId;

    @NotNull(message = "Material ID is mandatory")
    private UUID materialId;

    @NotNull(message = "Actual quantity is mandatory")
    private BigDecimal actualQuantity;

    @NotBlank(message = "Reason is mandatory")
    private String reason;

    private String evidence;
}

package com.memento3d.inventory.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to register a goods receipt (Stock In - F04)")
public class CreateStockReceiptRequest {

    @NotNull(message = "Warehouse ID is required")
    @Schema(description = "Destination warehouse ID")
    private UUID warehouseId;

    @NotNull(message = "Material ID is required")
    @Schema(description = "Material item ID")
    private UUID materialId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    @Schema(description = "Quantity in grams", example = "2000.00")
    private BigDecimal quantityGram;

    @NotNull(message = "Unit cost is required")
    @DecimalMin(value = "0.0", message = "Unit cost must be non-negative")
    @Schema(description = "Unit cost in VND per gram", example = "250.00")
    private BigDecimal unitCost;

    @Schema(description = "Supplier name", example = "eSun Official Store")
    private String supplier;

    @Schema(description = "Batch/Lot number", example = "LOT-202610-PLA")
    private String batchNumber;

    @NotBlank(message = "Idempotency key is required")
    @Schema(description = "Unique client-generated key to prevent duplicate receipt on network retry")
    private String idempotencyKey;

    @Schema(description = "Notes or invoice reference")
    private String note;
}

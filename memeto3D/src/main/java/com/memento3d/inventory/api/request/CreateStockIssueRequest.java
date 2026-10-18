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
@Schema(description = "Request to manually issue material from stock (Stock Out - F05)")
public class CreateStockIssueRequest {

    @NotNull(message = "Warehouse ID is required")
    @Schema(description = "Source warehouse ID")
    private UUID warehouseId;

    @NotNull(message = "Material ID is required")
    @Schema(description = "Material item ID")
    private UUID materialId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than zero")
    @Schema(description = "Quantity in grams to issue", example = "500.00")
    private BigDecimal quantityGram;

    @NotBlank(message = "Reason is required")
    @Schema(description = "Reason for issue (DAMAGED, EXPIRED, CALIBRATION, SCRAP, OTHER)", example = "CALIBRATION")
    private String reason;

    @NotBlank(message = "Idempotency key is required")
    @Schema(description = "Unique client-generated key to prevent duplicate issue")
    private String idempotencyKey;

    @Schema(description = "Optional notes or audit explanation")
    private String note;
}

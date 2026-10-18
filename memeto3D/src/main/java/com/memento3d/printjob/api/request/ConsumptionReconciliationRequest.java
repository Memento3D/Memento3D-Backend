package com.memento3d.printjob.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to reconcile actual consumption after printing (F07). Formula: issued = used + waste + returned")
public class ConsumptionReconciliationRequest {

    @NotNull(message = "Used quantity is required")
    @DecimalMin(value = "0.0", message = "Used quantity must be non-negative")
    @Schema(description = "Quantity in grams successfully printed into part", example = "350.00")
    private BigDecimal usedGram;

    @NotNull(message = "Waste quantity is required")
    @DecimalMin(value = "0.0", message = "Waste quantity must be non-negative")
    @Schema(description = "Supports, prime tower, or purge waste in grams", example = "30.00")
    private BigDecimal wasteGram;

    @NotNull(message = "Returned quantity is required")
    @DecimalMin(value = "0.0", message = "Returned quantity must be non-negative")
    @Schema(description = "Unused filament returned to warehouse stock", example = "20.00")
    private BigDecimal returnedGram;

    @Schema(description = "Quality control or post-print notes")
    private String note;
}

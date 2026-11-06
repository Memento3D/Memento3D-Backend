package com.memento3d.printjob.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(description = "Request to create a production Print Job")
@JsonIgnoreProperties(value = {"isOutsource", "outsource", "partnerName"})
public class CreatePrintJobRequest {

    @Schema(description = "Optional customer print order ID")
    private UUID orderId;

    @Schema(description = "Associated order item ID")
    private UUID orderItemId;

    @Schema(description = "Approved model version ID")
    private UUID modelVersionId;

    @Schema(description = "Assigned printer ID")
    private UUID printerId;

    @NotNull(message = "Material ID is required")
    @Schema(description = "Selected material ID")
    private UUID materialId;

    @NotNull(message = "Estimated quantity is required")
    @DecimalMin(value = "0.01", message = "Estimated material must be greater than zero")
    @Schema(description = "Estimated material in grams", example = "400.00")
    private BigDecimal estimatedGram;

    @Schema(description = "Estimated duration in seconds", example = "14400")
    private Integer estimatedDurationSeconds;
}

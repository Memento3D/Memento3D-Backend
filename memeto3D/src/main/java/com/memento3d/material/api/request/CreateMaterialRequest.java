package com.memento3d.material.api.request;

import com.memento3d.material.domain.model.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to register a new material in the catalog")
public class CreateMaterialRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @Schema(description = "Unique SKU", example = "PLA-WHT-001")
    private String sku;

    @NotBlank(message = "Material name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    @Schema(description = "Display name", example = "eSun PLA+ Cold White")
    private String name;

    @NotNull(message = "Material type is required")
    @Schema(description = "Type of filament or resin", example = "PLA")
    private MaterialType materialType;

    @NotBlank(message = "Color is required")
    @Size(max = 50, message = "Color must not exceed 50 characters")
    @Schema(description = "Color label", example = "White")
    private String color;

    @Size(max = 10, message = "Color hex must not exceed 10 characters")
    @Schema(description = "HEX color code", example = "#FFFFFF")
    private String colorHex;

    @DecimalMin(value = "0.0", message = "Density must be non-negative")
    @Schema(description = "Density in g/cm³", example = "1.24")
    private BigDecimal density;

    @DecimalMin(value = "0.0", message = "Diameter must be non-negative")
    @Schema(description = "Diameter in mm", example = "1.75")
    private BigDecimal diameter;

    @Schema(description = "Min nozzle print temp °C", example = "205")
    private Integer printTempMin;

    @Schema(description = "Max nozzle print temp °C", example = "225")
    private Integer printTempMax;

    @Schema(description = "Min heated bed temp °C", example = "55")
    private Integer bedTempMin;

    @Schema(description = "Max heated bed temp °C", example = "65")
    private Integer bedTempMax;

    @NotNull(message = "Unit cost is required")
    @DecimalMin(value = "0.0", message = "Unit cost must be non-negative")
    @Schema(description = "Standard cost per gram in VND", example = "250.00")
    private BigDecimal unitCost;

    @Schema(description = "Optional notes or technical description")
    private String description;
}

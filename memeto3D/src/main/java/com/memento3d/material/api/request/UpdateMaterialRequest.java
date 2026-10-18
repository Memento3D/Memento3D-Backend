package com.memento3d.material.api.request;

import com.memento3d.material.domain.model.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(description = "Request to update an existing material")
public class UpdateMaterialRequest {

    @Size(max = 200, message = "Name must not exceed 200 characters")
    @Schema(description = "Display name", example = "eSun PLA+ Cold White v2")
    private String name;

    @Schema(description = "Category", example = "Standard")
    private String category;

    @Schema(description = "Measurement unit", example = "g")
    private String unit;

    @Schema(description = "Manufacturer brand", example = "eSun")
    private String brand;

    @Schema(description = "Type of filament or resin", example = "PLA")
    private MaterialType materialType;

    @Size(max = 50, message = "Color must not exceed 50 characters")
    @Schema(description = "Color label", example = "Pure White")
    private String color;

    @Size(max = 10, message = "Color hex must not exceed 10 characters")
    @Schema(description = "HEX color code", example = "#FAFAFA")
    private String colorHex;

    @DecimalMin(value = "0.0", message = "Density must be non-negative")
    @Schema(description = "Density in g/cm³", example = "1.25")
    private BigDecimal density;

    @DecimalMin(value = "0.0", message = "Diameter must be non-negative")
    @Schema(description = "Diameter in mm", example = "1.75")
    private BigDecimal diameter;

    @Schema(description = "Min nozzle print temp °C", example = "210")
    private Integer printTempMin;

    @Schema(description = "Max nozzle print temp °C", example = "230")
    private Integer printTempMax;

    @Schema(description = "Min heated bed temp °C", example = "55")
    private Integer bedTempMin;

    @Schema(description = "Max heated bed temp °C", example = "65")
    private Integer bedTempMax;

    @DecimalMin(value = "0.0", message = "Unit cost must be non-negative")
    @Schema(description = "Standard cost per gram in VND", example = "260.00")
    private BigDecimal unitCost;

    @DecimalMin(value = "0.0", message = "Minimum stock threshold must be non-negative")
    @Schema(description = "Minimum stock threshold before alert", example = "600.00")
    private BigDecimal minimumStock;

    @Schema(description = "Optional notes or technical description")
    private String description;
}

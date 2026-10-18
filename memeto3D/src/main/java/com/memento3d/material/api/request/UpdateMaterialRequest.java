package com.memento3d.material.api.request;

import com.memento3d.material.domain.model.MaterialStatus;
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

    @Size(max = 100, message = "SKU must not exceed 100 characters")
    @Schema(description = "Updated unique SKU", example = "PLA-WHT-001")
    private String sku;

    @Size(max = 200, message = "Name must not exceed 200 characters")
    @Schema(description = "Updated display name")
    private String name;

    @Schema(description = "Updated material type")
    private MaterialType materialType;

    @Size(max = 50, message = "Color must not exceed 50 characters")
    @Schema(description = "Updated color label")
    private String color;

    @Size(max = 10, message = "Color hex must not exceed 10 characters")
    @Schema(description = "Updated HEX color code")
    private String colorHex;

    @DecimalMin(value = "0.0", message = "Density must be non-negative")
    private BigDecimal density;

    @DecimalMin(value = "0.0", message = "Diameter must be non-negative")
    private BigDecimal diameter;

    private Integer printTempMin;
    private Integer printTempMax;
    private Integer bedTempMin;
    private Integer bedTempMax;

    @DecimalMin(value = "0.0", message = "Unit cost must be non-negative")
    @Schema(description = "Updated cost per gram in VND")
    private BigDecimal unitCost;

    @Schema(description = "Status: ACTIVE or INACTIVE")
    private MaterialStatus status;

    private String description;
}

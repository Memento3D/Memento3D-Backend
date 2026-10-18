package com.memento3d.material.api.response;

import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.domain.model.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Material item details")
public class MaterialResponse {

    private UUID id;
    private String sku;
    private String name;
    private MaterialType materialType;
    private String color;
    private String colorHex;
    private BigDecimal density;
    private BigDecimal diameter;
    private Integer printTempMin;
    private Integer printTempMax;
    private Integer bedTempMin;
    private Integer bedTempMax;
    private BigDecimal unitCost;
    private MaterialStatus status;
    private String description;
    private UUID createdBy;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static MaterialResponse fromEntity(Material entity) {
        if (entity == null) return null;
        return MaterialResponse.builder()
                .id(entity.getId())
                .sku(entity.getSku())
                .name(entity.getName())
                .materialType(entity.getMaterialType())
                .color(entity.getColor())
                .colorHex(entity.getColorHex())
                .density(entity.getDensity())
                .diameter(entity.getDiameter())
                .printTempMin(entity.getPrintTempMin())
                .printTempMax(entity.getPrintTempMax())
                .bedTempMin(entity.getBedTempMin())
                .bedTempMax(entity.getBedTempMax())
                .unitCost(entity.getUnitCost())
                .status(entity.getStatus())
                .description(entity.getDescription())
                .createdBy(entity.getCreatedBy())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

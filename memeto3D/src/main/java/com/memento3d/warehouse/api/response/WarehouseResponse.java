package com.memento3d.warehouse.api.response;

import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Warehouse facility details")
public class WarehouseResponse {

    private UUID id;
    private String code;
    private String name;
    private String location;
    private WarehouseStatus status;
    private UUID createdBy;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static WarehouseResponse fromEntity(Warehouse entity) {
        if (entity == null) return null;
        return WarehouseResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .location(entity.getLocation())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

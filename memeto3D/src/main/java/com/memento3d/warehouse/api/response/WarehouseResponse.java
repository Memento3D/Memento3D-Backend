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
    private String address;
    private String description;
    private WarehouseStatus status;
    private UUID managerId;
    private UUID createdBy;
    private UUID updatedBy;
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
                .address(entity.getAddress())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .managerId(entity.getManagerId())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

package com.memento3d.warehouse.api.response;

import com.memento3d.warehouse.domain.model.MembershipStatus;
import com.memento3d.warehouse.domain.model.WarehouseMembership;
import com.memento3d.warehouse.domain.model.WarehouseRole;
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
@Schema(description = "Warehouse membership details")
public class WarehouseMembershipResponse {

    private UUID id;
    private UUID warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private UUID userId;
    private String userEmail;
    private String userFullName;
    private WarehouseRole role;
    private MembershipStatus status;
    private UUID assignedBy;
    private Instant assignedAt;
    private Instant revokedAt;

    public static WarehouseMembershipResponse fromEntity(WarehouseMembership entity) {
        if (entity == null) return null;
        return WarehouseMembershipResponse.builder()
                .id(entity.getId())
                .warehouseId(entity.getWarehouse() != null ? entity.getWarehouse().getId() : null)
                .warehouseCode(entity.getWarehouse() != null ? entity.getWarehouse().getCode() : null)
                .warehouseName(entity.getWarehouse() != null ? entity.getWarehouse().getName() : null)
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userEmail(entity.getUser() != null ? entity.getUser().getEmail() : null)
                .userFullName(entity.getUser() != null ? entity.getUser().getFullName() : null)
                .role(entity.getRole())
                .status(entity.getStatus())
                .assignedBy(entity.getAssignedBy())
                .assignedAt(entity.getAssignedAt())
                .revokedAt(entity.getRevokedAt())
                .build();
    }
}

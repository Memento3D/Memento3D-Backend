package com.memento3d.warehouse.api.request;

import com.memento3d.warehouse.domain.model.WarehouseRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to assign a staff member to a warehouse")
public class AssignMemberRequest {

    @Schema(description = "Target user ID (required if email is not provided)")
    private UUID userId;

    @Schema(description = "Target user email (required if userId is not provided)")
    private String email;

    @NotNull(message = "Warehouse role is required (MANAGER, WAREHOUSE_STAFF, PRINT_OPERATOR)")
    @Schema(description = "Warehouse-scoped role", example = "WAREHOUSE_STAFF")
    private WarehouseRole role;
}

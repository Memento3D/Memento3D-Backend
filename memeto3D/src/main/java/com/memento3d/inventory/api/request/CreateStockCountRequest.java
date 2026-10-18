package com.memento3d.inventory.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStockCountRequest {

    @NotNull(message = "Warehouse ID is mandatory")
    private UUID warehouseId;

    private String note;
}

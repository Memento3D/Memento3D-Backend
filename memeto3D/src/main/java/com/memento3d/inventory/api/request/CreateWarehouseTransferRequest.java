package com.memento3d.inventory.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWarehouseTransferRequest {

    @NotNull(message = "Source warehouse ID is mandatory")
    private UUID sourceWarehouseId;

    @NotNull(message = "Destination warehouse ID is mandatory")
    private UUID destinationWarehouseId;

    private String note;

    @NotEmpty(message = "Transfer must contain at least one item")
    @Valid
    private List<TransferItemRequest> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferItemRequest {
        @NotNull(message = "Material ID is mandatory")
        private UUID materialId;

        @NotNull(message = "Quantity is mandatory")
        private BigDecimal quantity;

        private String note;
    }
}

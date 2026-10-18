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
public class UpdateStockCountItemsRequest {

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<CountItemUpdate> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CountItemUpdate {
        @NotNull(message = "Material ID is mandatory")
        private UUID materialId;

        @NotNull(message = "Counted quantity is mandatory")
        private BigDecimal countedQuantity;

        private String note;
    }
}

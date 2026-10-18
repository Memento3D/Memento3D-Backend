package com.memento3d.inventory.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class CreateEnterpriseStockIssueRequest {

    @NotNull(message = "Warehouse ID is mandatory")
    private UUID warehouseId;

    @NotBlank(message = "Reason is mandatory")
    private String reason;

    private String referenceType;

    private String referenceId;

    private String note;

    @NotEmpty(message = "Stock issue must contain at least one item")
    @Valid
    private List<EnterpriseStockIssueItemRequest> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnterpriseStockIssueItemRequest {
        @NotNull(message = "Material ID is mandatory")
        private UUID materialId;

        @NotNull(message = "Requested quantity is mandatory")
        private BigDecimal requestedQuantity;

        private String note;
    }
}

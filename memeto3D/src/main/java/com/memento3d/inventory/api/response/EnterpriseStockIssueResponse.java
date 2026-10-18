package com.memento3d.inventory.api.response;

import com.memento3d.inventory.domain.model.EnterpriseStockIssueStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnterpriseStockIssueResponse {
    private UUID id;
    private String issueCode;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseCode;
    private String referenceType;
    private String referenceId;
    private String reason;
    private EnterpriseStockIssueStatus status;
    private String note;
    private List<EnterpriseStockIssueItemResponse> items;
    private UUID createdBy;
    private UUID approvedBy;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnterpriseStockIssueItemResponse {
        private UUID id;
        private UUID materialId;
        private String materialName;
        private String materialSku;
        private BigDecimal requestedQuantity;
        private BigDecimal issuedQuantity;
        private String note;
    }
}

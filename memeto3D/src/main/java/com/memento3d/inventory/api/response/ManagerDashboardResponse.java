package com.memento3d.inventory.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerDashboardResponse {
    private long totalWarehouses;
    private long totalMaterials;
    private long totalInventoryItems;
    private long lowStockCount;
    private long outOfStockCount;
    private long pendingGoodsReceipts;
    private long pendingStockIssues;
    private long pendingAdjustments;
    private long pendingTransfers;
    private BigDecimal totalInventoryValue;
    private List<InventoryBalanceResponse> lowStockItems;
    private List<InventoryTransactionResponse> recentStockMovements;
}

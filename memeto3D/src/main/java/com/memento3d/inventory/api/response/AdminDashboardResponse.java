package com.memento3d.inventory.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long totalManagers;
    private long totalWarehouseStaff;
    private long totalWarehouses;
    private long totalMaterials;
    private long lowStockCount;
    private long outOfStockCount;
    private long pendingApprovals;
    private BigDecimal totalInventoryValue;
    private List<InventoryTransactionResponse> recentMovements;
}

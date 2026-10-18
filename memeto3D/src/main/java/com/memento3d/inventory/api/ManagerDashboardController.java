package com.memento3d.inventory.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.response.InventoryBalanceResponse;
import com.memento3d.inventory.api.response.InventoryTransactionResponse;
import com.memento3d.inventory.api.response.ManagerDashboardResponse;
import com.memento3d.inventory.application.service.InventoryService;
import com.memento3d.inventory.domain.model.EnterpriseStockIssueStatus;
import com.memento3d.inventory.domain.model.GoodsReceiptStatus;
import com.memento3d.inventory.domain.model.StockAdjustmentStatus;
import com.memento3d.inventory.domain.model.WarehouseTransferStatus;
import com.memento3d.inventory.infrastructure.persistence.repository.*;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manager/dashboard")
@RequiredArgsConstructor
@Tag(name = "Manager Dashboard", description = "Realtime inventory metrics, low stock alerts, and approval queues for Managers")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE', 'OPERATOR')")
public class ManagerDashboardController {

    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final EnterpriseStockIssueRepository stockIssueRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final WarehouseTransferRepository warehouseTransferRepository;
    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "Get aggregated manager dashboard metrics and alerts")
    public ResponseEntity<ManagerDashboardResponse> getDashboardMetrics(
            @RequestParam(name = "warehouseId", required = false) UUID warehouseId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        long totalWarehouses = warehouseRepository.countByStatus(WarehouseStatus.ACTIVE);
        long totalMaterials = materialRepository.countByStatus(MaterialStatus.ACTIVE);
        long totalInventoryItems = inventoryBalanceRepository.count();

        long lowStockCount = inventoryBalanceRepository.countLowStock(warehouseId);
        long outOfStockCount = inventoryBalanceRepository.countOutOfStock(warehouseId);
        BigDecimal totalValue = inventoryBalanceRepository.calculateTotalInventoryValue(warehouseId);

        long pendingReceipts = goodsReceiptRepository.countByStatus(GoodsReceiptStatus.SUBMITTED);
        long pendingIssues = stockIssueRepository.countByStatus(EnterpriseStockIssueStatus.SUBMITTED);
        long pendingAdjustments = stockAdjustmentRepository.countByStatus(StockAdjustmentStatus.SUBMITTED);
        long pendingTransfers = warehouseTransferRepository.countByStatus(WarehouseTransferStatus.SUBMITTED);

        List<InventoryBalanceResponse> lowStockItems = inventoryBalanceRepository.findLowStockItems(warehouseId).stream()
                .map(InventoryBalanceResponse::fromEntity)
                .limit(10)
                .toList();

        List<InventoryTransactionResponse> recentMovements = inventoryService
                .listTransactions(warehouseId, null, null, null, PageRequest.of(0, 10))
                .getContent();

        ManagerDashboardResponse response = ManagerDashboardResponse.builder()
                .totalWarehouses(totalWarehouses)
                .totalMaterials(totalMaterials)
                .totalInventoryItems(totalInventoryItems)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .pendingGoodsReceipts(pendingReceipts)
                .pendingStockIssues(pendingIssues)
                .pendingAdjustments(pendingAdjustments)
                .pendingTransfers(pendingTransfers)
                .totalInventoryValue(totalValue != null ? totalValue : BigDecimal.ZERO)
                .lowStockItems(lowStockItems)
                .recentStockMovements(recentMovements)
                .build();

        return ResponseEntity.ok(response);
    }
}

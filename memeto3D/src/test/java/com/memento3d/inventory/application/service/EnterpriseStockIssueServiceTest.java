package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.response.EnterpriseStockIssueResponse;
import com.memento3d.inventory.application.service.impl.EnterpriseStockIssueServiceImpl;
import com.memento3d.inventory.domain.model.*;
import com.memento3d.inventory.infrastructure.persistence.repository.*;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.warehouse.application.service.WarehouseSecurityService;
import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnterpriseStockIssueServiceTest {

    @Mock private EnterpriseStockIssueRepository issueRepository;
    @Mock private EnterpriseStockIssueItemRepository issueItemRepository;
    @Mock private InventoryBalanceRepository balanceRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private WarehouseSecurityService securityService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EnterpriseStockIssueServiceImpl stockIssueService;

    private UUID warehouseId;
    private UUID materialId;
    private UUID actorId;
    private Warehouse mockWarehouse;
    private Material mockMaterial;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        materialId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        mockWarehouse = Warehouse.builder()
                .id(warehouseId)
                .code("WH-TEST-01")
                .name("Main Testing Warehouse")
                .status(WarehouseStatus.ACTIVE)
                .build();

        mockMaterial = Material.builder()
                .id(materialId)
                .sku("PETG-CLR-001")
                .name("Sunlu PETG Clear")
                .status(MaterialStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Complete Stock Issue: deducts stock atomically and records STOCK_OUT")
    void completeStockIssue_success() {
        UUID issueId = UUID.randomUUID();
        EnterpriseStockIssue issue = EnterpriseStockIssue.builder()
                .id(issueId)
                .issueCode("SI-2026-0001")
                .warehouse(mockWarehouse)
                .status(EnterpriseStockIssueStatus.APPROVED)
                .build();

        EnterpriseStockIssueItem item = EnterpriseStockIssueItem.builder()
                .id(UUID.randomUUID())
                .issue(issue)
                .material(mockMaterial)
                .requestedQuantity(new BigDecimal("20.00"))
                .issuedQuantity(new BigDecimal("20.00"))
                .build();

        issue.setItems(List.of(item));

        InventoryBalance balance = InventoryBalance.builder()
                .id(UUID.randomUUID())
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .onHandGram(new BigDecimal("100.00"))
                .reservedGram(BigDecimal.ZERO)
                .build();

        when(issueRepository.findWithItemsById(issueId)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any(EnterpriseStockIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(balance));

        EnterpriseStockIssueResponse res = stockIssueService.completeIssue(issueId, actorId);

        assertThat(res.getStatus()).isEqualTo(EnterpriseStockIssueStatus.COMPLETED);
        assertThat(balance.getOnHandGram()).isEqualByComparingTo("80.00");
        verify(transactionRepository).save(any(InventoryTransaction.class));
    }

    @Test
    @DisplayName("Complete Stock Issue: throws INSUFFICIENT_STOCK when available stock is less than requested")
    void completeStockIssue_insufficientStock_throwsException() {
        UUID issueId = UUID.randomUUID();
        EnterpriseStockIssue issue = EnterpriseStockIssue.builder()
                .id(issueId)
                .issueCode("SI-2026-0002")
                .warehouse(mockWarehouse)
                .status(EnterpriseStockIssueStatus.APPROVED)
                .build();

        EnterpriseStockIssueItem item = EnterpriseStockIssueItem.builder()
                .id(UUID.randomUUID())
                .issue(issue)
                .material(mockMaterial)
                .requestedQuantity(new BigDecimal("120.00"))
                .issuedQuantity(new BigDecimal("120.00"))
                .build();

        issue.setItems(List.of(item));

        InventoryBalance balance = InventoryBalance.builder()
                .id(UUID.randomUUID())
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .onHandGram(new BigDecimal("100.00"))
                .reservedGram(BigDecimal.ZERO)
                .build();

        when(issueRepository.findWithItemsById(issueId)).thenReturn(Optional.of(issue));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> stockIssueService.completeIssue(issueId, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient available stock");
    }
}

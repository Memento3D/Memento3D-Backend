package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.response.WarehouseTransferResponse;
import com.memento3d.inventory.application.service.impl.WarehouseTransferServiceImpl;
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
class WarehouseTransferServiceTest {

    @Mock private WarehouseTransferRepository transferRepository;
    @Mock private WarehouseTransferItemRepository transferItemRepository;
    @Mock private InventoryBalanceRepository balanceRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private WarehouseSecurityService securityService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private WarehouseTransferServiceImpl transferService;

    private UUID sourceWhId;
    private UUID destWhId;
    private UUID materialId;
    private UUID actorId;
    private Warehouse sourceWh;
    private Warehouse destWh;
    private Material mockMaterial;

    @BeforeEach
    void setUp() {
        sourceWhId = UUID.randomUUID();
        destWhId = UUID.randomUUID();
        materialId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        sourceWh = Warehouse.builder().id(sourceWhId).code("WH-SRC").name("Source Hub").status(WarehouseStatus.ACTIVE).build();
        destWh = Warehouse.builder().id(destWhId).code("WH-DST").name("Dest Hub").status(WarehouseStatus.ACTIVE).build();

        mockMaterial = Material.builder().id(materialId).sku("ABS-BLK-001").name("ABS Black").status(MaterialStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("Ship Transfer: transitions to IN_TRANSIT, deducts source stock, and records TRANSFER_OUT")
    void shipTransfer_success() {
        UUID transferId = UUID.randomUUID();
        WarehouseTransfer transfer = WarehouseTransfer.builder()
                .id(transferId)
                .transferCode("TR-2026-0001")
                .sourceWarehouse(sourceWh)
                .destinationWarehouse(destWh)
                .status(WarehouseTransferStatus.APPROVED)
                .build();

        WarehouseTransferItem item = WarehouseTransferItem.builder()
                .id(UUID.randomUUID())
                .transfer(transfer)
                .material(mockMaterial)
                .quantity(new BigDecimal("30.00"))
                .build();
        transfer.setItems(List.of(item));

        InventoryBalance srcBalance = InventoryBalance.builder()
                .id(UUID.randomUUID())
                .warehouse(sourceWh)
                .material(mockMaterial)
                .onHandGram(new BigDecimal("100.00"))
                .reservedGram(BigDecimal.ZERO)
                .build();

        when(transferRepository.findWithItemsById(transferId)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(WarehouseTransfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(sourceWhId, materialId))
                .thenReturn(Optional.of(srcBalance));

        WarehouseTransferResponse res = transferService.shipTransfer(transferId, actorId);

        assertThat(res.getStatus()).isEqualTo(WarehouseTransferStatus.IN_TRANSIT);
        assertThat(srcBalance.getOnHandGram()).isEqualByComparingTo("70.00");
        verify(transactionRepository).save(any(InventoryTransaction.class));
    }

    @Test
    @DisplayName("Receive Transfer: transitions to RECEIVED, credits destination stock, and records TRANSFER_IN")
    void receiveTransfer_success() {
        UUID transferId = UUID.randomUUID();
        WarehouseTransfer transfer = WarehouseTransfer.builder()
                .id(transferId)
                .transferCode("TR-2026-0001")
                .sourceWarehouse(sourceWh)
                .destinationWarehouse(destWh)
                .status(WarehouseTransferStatus.IN_TRANSIT)
                .build();

        WarehouseTransferItem item = WarehouseTransferItem.builder()
                .id(UUID.randomUUID())
                .transfer(transfer)
                .material(mockMaterial)
                .quantity(new BigDecimal("30.00"))
                .build();
        transfer.setItems(List.of(item));

        InventoryBalance dstBalance = InventoryBalance.builder()
                .id(UUID.randomUUID())
                .warehouse(destWh)
                .material(mockMaterial)
                .onHandGram(new BigDecimal("10.00"))
                .reservedGram(BigDecimal.ZERO)
                .build();

        when(transferRepository.findWithItemsById(transferId)).thenReturn(Optional.of(transfer));
        when(transferRepository.save(any(WarehouseTransfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(destWhId, materialId))
                .thenReturn(Optional.of(dstBalance));

        WarehouseTransferResponse res = transferService.receiveTransfer(transferId, actorId);

        assertThat(res.getStatus()).isEqualTo(WarehouseTransferStatus.RECEIVED);
        assertThat(dstBalance.getOnHandGram()).isEqualByComparingTo("40.00");
        verify(transactionRepository).save(any(InventoryTransaction.class));
    }
}

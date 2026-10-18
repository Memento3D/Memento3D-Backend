package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateGoodsReceiptRequest;
import com.memento3d.inventory.api.response.GoodsReceiptResponse;
import com.memento3d.inventory.application.service.impl.GoodsReceiptServiceImpl;
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
class GoodsReceiptServiceTest {

    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptItemRepository goodsReceiptItemRepository;
    @Mock private InventoryBalanceRepository inventoryBalanceRepository;
    @Mock private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private WarehouseSecurityService securityService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private GoodsReceiptServiceImpl goodsReceiptService;

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
                .sku("PLA-BLK-001")
                .name("eSun PLA+ Black")
                .status(MaterialStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Create Goods Receipt draft: stores receipt and line items in DRAFT state")
    void createGoodsReceipt_draft_success() {
        CreateGoodsReceiptRequest.GoodsReceiptItemRequest itemReq = CreateGoodsReceiptRequest.GoodsReceiptItemRequest.builder()
                .materialId(materialId)
                .quantity(new BigDecimal("100.00"))
                .unitCost(new BigDecimal("22.50"))
                .build();

        CreateGoodsReceiptRequest req = CreateGoodsReceiptRequest.builder()
                .warehouseId(warehouseId)
                .supplier("Polymaker Official")
                .note("PO-2026-001")
                .items(List.of(itemReq))
                .build();

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(mockMaterial));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(invocation -> {
            GoodsReceipt gr = invocation.getArgument(0);
            gr.setId(UUID.randomUUID());
            return gr;
        });

        GoodsReceiptResponse response = goodsReceiptService.createReceipt(req, actorId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(GoodsReceiptStatus.DRAFT);
        assertThat(response.getSupplier()).isEqualTo("Polymaker Official");
        verify(goodsReceiptRepository).save(any(GoodsReceipt.class));
    }

    @Test
    @DisplayName("Complete Goods Receipt: transitions to COMPLETED, updates stock atomically, and records STOCK_IN")
    void completeGoodsReceipt_success() {
        UUID receiptId = UUID.randomUUID();
        GoodsReceipt receipt = GoodsReceipt.builder()
                .id(receiptId)
                .receiptCode("GR-2026-0001")
                .warehouse(mockWarehouse)
                .status(GoodsReceiptStatus.APPROVED)
                .build();

        GoodsReceiptItem item = GoodsReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .material(mockMaterial)
                .quantity(new BigDecimal("50.00"))
                .build();

        receipt.setItems(List.of(item));

        InventoryBalance balance = InventoryBalance.builder()
                .id(UUID.randomUUID())
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .onHandGram(new BigDecimal("100.00"))
                .reservedGram(BigDecimal.ZERO)
                .build();

        when(goodsReceiptRepository.findWithItemsById(receiptId)).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryBalanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(balance));

        GoodsReceiptResponse res = goodsReceiptService.completeReceipt(receiptId, actorId);

        assertThat(res.getStatus()).isEqualTo(GoodsReceiptStatus.COMPLETED);
        assertThat(balance.getOnHandGram()).isEqualByComparingTo("150.00");
        verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
    }

    @Test
    @DisplayName("Complete Goods Receipt twice: rejects with conflict error")
    void completeGoodsReceipt_twice_fails() {
        UUID receiptId = UUID.randomUUID();
        GoodsReceipt receipt = GoodsReceipt.builder()
                .id(receiptId)
                .receiptCode("GR-2026-0001")
                .warehouse(mockWarehouse)
                .status(GoodsReceiptStatus.COMPLETED)
                .build();

        when(goodsReceiptRepository.findWithItemsById(receiptId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> goodsReceiptService.completeReceipt(receiptId, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already completed");
    }
}

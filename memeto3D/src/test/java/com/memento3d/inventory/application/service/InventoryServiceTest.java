package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateStockIssueRequest;
import com.memento3d.inventory.api.request.CreateStockReceiptRequest;
import com.memento3d.inventory.api.response.StockIssueResponse;
import com.memento3d.inventory.api.response.StockReceiptResponse;
import com.memento3d.inventory.application.service.impl.InventoryServiceImpl;
import com.memento3d.inventory.domain.model.InventoryBalance;
import com.memento3d.inventory.domain.model.StockIssue;
import com.memento3d.inventory.domain.model.StockReceipt;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockIssueRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockReceiptRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.shared.exception.BusinessException;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryBalanceRepository balanceRepository;
    @Mock private StockReceiptRepository receiptRepository;
    @Mock private StockIssueRepository issueRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MaterialRepository materialRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private UUID warehouseId;
    private UUID materialId;
    private Warehouse mockWarehouse;
    private Material mockMaterial;
    private InventoryBalance mockBalance;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        materialId = UUID.randomUUID();

        mockWarehouse = Warehouse.builder()
                .id(warehouseId)
                .code("WH-HCM-01")
                .name("Thu Duc Hub")
                .status(WarehouseStatus.ACTIVE)
                .build();

        mockMaterial = Material.builder()
                .id(materialId)
                .sku("PLA-WHT-001")
                .name("eSun PLA+ Cold White")
                .status(MaterialStatus.ACTIVE)
                .build();

        mockBalance = InventoryBalance.builder()
                .id(UUID.randomUUID())
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .onHandGram(new BigDecimal("1000.00"))
                .reservedGram(new BigDecimal("200.00"))
                .build();
    }

    @Test
    @DisplayName("createReceipt: increments on-hand stock and saves ledger transaction (F04)")
    void createReceipt_success() {
        CreateStockReceiptRequest request = CreateStockReceiptRequest.builder()
                .warehouseId(warehouseId)
                .materialId(materialId)
                .quantityGram(new BigDecimal("500.00"))
                .unitCost(new BigDecimal("250.00"))
                .idempotencyKey("KEY-REC-1")
                .build();

        when(receiptRepository.findByIdempotencyKey("KEY-REC-1")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(mockMaterial));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(mockBalance));
        when(receiptRepository.save(any(StockReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        StockReceiptResponse response = inventoryService.createReceipt(request, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(mockBalance.getOnHandGram()).isEqualTo(new BigDecimal("1500.00"));
        verify(balanceRepository).save(mockBalance);
        verify(transactionRepository).save(any());
    }

    @Test
    @DisplayName("createReceipt: returns existing receipt when idempotency key is repeated (Idempotency)")
    void createReceipt_idempotency() {
        StockReceipt existingReceipt = StockReceipt.builder()
                .id(UUID.randomUUID())
                .receiptNumber("REC-EXISTING")
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .quantityGram(new BigDecimal("500.00"))
                .idempotencyKey("KEY-REC-DUP")
                .build();

        CreateStockReceiptRequest request = CreateStockReceiptRequest.builder()
                .warehouseId(warehouseId)
                .materialId(materialId)
                .quantityGram(new BigDecimal("500.00"))
                .unitCost(new BigDecimal("250.00"))
                .idempotencyKey("KEY-REC-DUP")
                .build();

        when(receiptRepository.findByIdempotencyKey("KEY-REC-DUP")).thenReturn(Optional.of(existingReceipt));

        StockReceiptResponse response = inventoryService.createReceipt(request, UUID.randomUUID());

        assertThat(response.getReceiptNumber()).isEqualTo("REC-EXISTING");
        verify(balanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("createIssue: decrements on-hand stock when available quantity is sufficient (F05)")
    void createIssue_success() {
        CreateStockIssueRequest request = CreateStockIssueRequest.builder()
                .warehouseId(warehouseId)
                .materialId(materialId)
                .quantityGram(new BigDecimal("300.00"))
                .reason("CALIBRATION")
                .idempotencyKey("KEY-ISS-1")
                .build();

        when(issueRepository.findByIdempotencyKey("KEY-ISS-1")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(mockMaterial));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(mockBalance));
        when(issueRepository.save(any(StockIssue.class))).thenAnswer(inv -> inv.getArgument(0));

        StockIssueResponse response = inventoryService.createIssue(request, UUID.randomUUID());

        assertThat(response).isNotNull();
        // 1000 - 300 = 700
        assertThat(mockBalance.getOnHandGram()).isEqualTo(new BigDecimal("700.00"));
        verify(balanceRepository).save(mockBalance);
        verify(transactionRepository).save(any());
    }

    @Test
    @DisplayName("createIssue: throws 409 Conflict when issue quantity exceeds available stock (F05)")
    void createIssue_insufficientStock() {
        // onHand: 1000, reserved: 200 => available = 800
        CreateStockIssueRequest request = CreateStockIssueRequest.builder()
                .warehouseId(warehouseId)
                .materialId(materialId)
                .quantityGram(new BigDecimal("900.00")) // exceeds 800g available
                .reason("SCRAP")
                .idempotencyKey("KEY-ISS-2")
                .build();

        when(issueRepository.findByIdempotencyKey("KEY-ISS-2")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(mockMaterial));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(mockBalance));

        assertThatThrownBy(() -> inventoryService.createIssue(request, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("INSUFFICIENT_STOCK");
                });

        verify(balanceRepository, never()).save(mockBalance);
    }
}

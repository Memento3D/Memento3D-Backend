package com.memento3d.inventory.application.service.impl;

import com.memento3d.inventory.api.request.CreateGoodsReceiptRequest;
import com.memento3d.inventory.api.request.UpdateGoodsReceiptRequest;
import com.memento3d.inventory.api.response.GoodsReceiptResponse;
import com.memento3d.inventory.application.service.GoodsReceiptService;
import com.memento3d.inventory.domain.model.*;
import com.memento3d.inventory.infrastructure.persistence.repository.GoodsReceiptItemRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.GoodsReceiptRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptItemRepository goodsReceiptItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public GoodsReceiptResponse createReceipt(CreateGoodsReceiptRequest request, UUID actorId) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + request.getWarehouseId()));

        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_INACTIVE",
                    "Cannot create goods receipt for inactive or maintenance warehouse: " + warehouse.getCode());
        }

        String receiptCode = "GR-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        GoodsReceipt receipt = GoodsReceipt.builder()
                .receiptCode(receiptCode)
                .warehouse(warehouse)
                .supplier(request.getSupplier())
                .status(GoodsReceiptStatus.DRAFT)
                .note(request.getNote())
                .createdBy(actorId)
                .build();

        for (CreateGoodsReceiptRequest.GoodsReceiptItemRequest itemReq : request.getItems()) {
            Material material = materialRepository.findById(itemReq.getMaterialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + itemReq.getMaterialId()));

            GoodsReceiptItem item = GoodsReceiptItem.builder()
                    .material(material)
                    .quantity(itemReq.getQuantity())
                    .unitCost(itemReq.getUnitCost() != null ? itemReq.getUnitCost() : material.getUnitCost())
                    .batchNumber(itemReq.getBatchNumber())
                    .note(itemReq.getNote())
                    .build();
            receipt.addItem(item);
        }

        GoodsReceipt saved = goodsReceiptRepository.save(receipt);
        log.info("Created GoodsReceipt ID={}, Code={}", saved.getId(), saved.getReceiptCode());

        auditLogService.log(actorId, "CREATE_GOODS_RECEIPT", "GoodsReceipt", saved.getId().toString(), null, "Created: " + receiptCode);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsReceiptResponse getReceipt(UUID id) {
        GoodsReceipt receipt = goodsReceiptRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods receipt not found: " + id));
        return mapToResponse(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GoodsReceiptResponse> listReceipts(UUID warehouseId, GoodsReceiptStatus status, Pageable pageable) {
        return goodsReceiptRepository.findAllWithFilters(warehouseId, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse updateReceipt(UUID id, UpdateGoodsReceiptRequest request, UUID actorId) {
        GoodsReceipt receipt = goodsReceiptRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods receipt not found: " + id));

        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only DRAFT goods receipts can be modified");
        }

        if (request.getSupplier() != null) receipt.setSupplier(request.getSupplier());
        if (request.getNote() != null) receipt.setNote(request.getNote());

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            receipt.getItems().clear();
            for (CreateGoodsReceiptRequest.GoodsReceiptItemRequest itemReq : request.getItems()) {
                Material material = materialRepository.findById(itemReq.getMaterialId())
                        .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + itemReq.getMaterialId()));

                GoodsReceiptItem item = GoodsReceiptItem.builder()
                        .material(material)
                        .quantity(itemReq.getQuantity())
                        .unitCost(itemReq.getUnitCost() != null ? itemReq.getUnitCost() : material.getUnitCost())
                        .batchNumber(itemReq.getBatchNumber())
                        .note(itemReq.getNote())
                        .build();
                receipt.addItem(item);
            }
        }

        GoodsReceipt saved = goodsReceiptRepository.save(receipt);
        auditLogService.log(actorId, "UPDATE_GOODS_RECEIPT", "GoodsReceipt", saved.getId().toString(), null, "Updated: " + saved.getReceiptCode());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse submitReceipt(UUID id, UUID actorId) {
        GoodsReceipt receipt = goodsReceiptRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods receipt not found: " + id));

        if (receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only DRAFT receipts can be submitted");
        }

        receipt.setStatus(GoodsReceiptStatus.SUBMITTED);
        GoodsReceipt saved = goodsReceiptRepository.save(receipt);
        auditLogService.log(actorId, "SUBMIT_GOODS_RECEIPT", "GoodsReceipt", saved.getId().toString(), "DRAFT", "SUBMITTED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse approveReceipt(UUID id, UUID actorId) {
        GoodsReceipt receipt = goodsReceiptRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods receipt not found: " + id));

        if (receipt.getStatus() != GoodsReceiptStatus.SUBMITTED && receipt.getStatus() != GoodsReceiptStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only SUBMITTED receipts can be approved");
        }

        receipt.setStatus(GoodsReceiptStatus.APPROVED);
        receipt.setApprovedBy(actorId);
        GoodsReceipt saved = goodsReceiptRepository.save(receipt);
        auditLogService.log(actorId, "APPROVE_GOODS_RECEIPT", "GoodsReceipt", saved.getId().toString(), "SUBMITTED", "APPROVED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse completeReceipt(UUID id, UUID actorId) {
        GoodsReceipt receipt = goodsReceiptRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods receipt not found: " + id));

        if (receipt.getStatus() == GoodsReceiptStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "RECEIPT_ALREADY_COMPLETED", "This goods receipt is already completed");
        }

        if (receipt.getStatus() != GoodsReceiptStatus.APPROVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only APPROVED receipts can be completed");
        }

        Warehouse warehouse = receipt.getWarehouse();
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_INACTIVE", "Cannot complete receipt into an inactive warehouse");
        }

        // Atomic inventory increment and immutable ledger recording
        for (GoodsReceiptItem item : receipt.getItems()) {
            Material material = item.getMaterial();
            BigDecimal qty = item.getQuantity();

            InventoryBalance balance = inventoryBalanceRepository
                    .findByWarehouseIdAndMaterialIdForUpdate(warehouse.getId(), material.getId())
                    .orElseGet(() -> {
                        InventoryBalance newBalance = InventoryBalance.builder()
                                .warehouse(warehouse)
                                .material(material)
                                .onHandGram(BigDecimal.ZERO)
                                .reservedGram(BigDecimal.ZERO)
                                .minimumStock(material.getMinimumStock() != null ? material.getMinimumStock() : new BigDecimal("500.00"))
                                .build();
                        return inventoryBalanceRepository.save(newBalance);
                    });

            BigDecimal beforeOnHand = balance.getOnHandGram();
            BigDecimal afterOnHand = beforeOnHand.add(qty);
            BigDecimal beforeReserved = balance.getReservedGram();

            balance.setOnHandGram(afterOnHand);
            inventoryBalanceRepository.save(balance);

            InventoryTransaction tx = InventoryTransaction.builder()
                    .transactionType(InventoryTransactionType.STOCK_IN)
                    .warehouse(warehouse)
                    .material(material)
                    .deltaOnHandGram(qty)
                    .deltaReservedGram(BigDecimal.ZERO)
                    .beforeOnHandGram(beforeOnHand)
                    .afterOnHandGram(afterOnHand)
                    .beforeReservedGram(beforeReserved)
                    .afterReservedGram(beforeReserved)
                    .referenceId(receipt.getReceiptCode())
                    .reason("Goods Receipt: " + (item.getBatchNumber() != null ? "Batch " + item.getBatchNumber() : "Supplier " + receipt.getSupplier()))
                    .unitCost(item.getUnitCost())
                    .performedBy(actorId)
                    .approvedBy(receipt.getApprovedBy())
                    .build();
            inventoryTransactionRepository.save(tx);
        }

        receipt.setStatus(GoodsReceiptStatus.COMPLETED);
        receipt.setCompletedAt(Instant.now());
        GoodsReceipt saved = goodsReceiptRepository.save(receipt);

        log.info("Completed GoodsReceipt ID={}, Code={}, Incremented inventory items={}", saved.getId(), saved.getReceiptCode(), saved.getItems().size());
        auditLogService.log(actorId, "COMPLETE_GOODS_RECEIPT", "GoodsReceipt", saved.getId().toString(), "APPROVED", "COMPLETED");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse cancelReceipt(UUID id, UUID actorId) {
        GoodsReceipt receipt = goodsReceiptRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods receipt not found: " + id));

        if (receipt.getStatus() == GoodsReceiptStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_CANCEL_COMPLETED", "Completed goods receipts cannot be cancelled. Use stock adjustment instead.");
        }

        receipt.setStatus(GoodsReceiptStatus.CANCELLED);
        GoodsReceipt saved = goodsReceiptRepository.save(receipt);
        auditLogService.log(actorId, "CANCEL_GOODS_RECEIPT", "GoodsReceipt", saved.getId().toString(), null, "CANCELLED");
        return mapToResponse(saved);
    }

    private GoodsReceiptResponse mapToResponse(GoodsReceipt receipt) {
        return GoodsReceiptResponse.builder()
                .id(receipt.getId())
                .receiptCode(receipt.getReceiptCode())
                .warehouseId(receipt.getWarehouse().getId())
                .warehouseName(receipt.getWarehouse().getName())
                .warehouseCode(receipt.getWarehouse().getCode())
                .supplier(receipt.getSupplier())
                .status(receipt.getStatus())
                .note(receipt.getNote())
                .items(receipt.getItems() != null ? receipt.getItems().stream().map(i -> GoodsReceiptResponse.GoodsReceiptItemResponse.builder()
                        .id(i.getId())
                        .materialId(i.getMaterial().getId())
                        .materialName(i.getMaterial().getName())
                        .materialSku(i.getMaterial().getSku())
                        .quantity(i.getQuantity())
                        .unitCost(i.getUnitCost())
                        .batchNumber(i.getBatchNumber())
                        .note(i.getNote())
                        .build()).toList() : null)
                .createdBy(receipt.getCreatedBy())
                .approvedBy(receipt.getApprovedBy())
                .completedAt(receipt.getCompletedAt())
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }
}

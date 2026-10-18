package com.memento3d.inventory.application.service.impl;

import com.memento3d.inventory.api.request.CreateStockCountRequest;
import com.memento3d.inventory.api.request.UpdateStockCountItemsRequest;
import com.memento3d.inventory.api.response.StockCountResponse;
import com.memento3d.inventory.application.service.StockCountService;
import com.memento3d.inventory.domain.model.*;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockAdjustmentRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockCountItemRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockCountRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockCountServiceImpl implements StockCountService {

    private final StockCountRepository stockCountRepository;
    private final StockCountItemRepository stockCountItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public StockCountResponse createStockCount(CreateStockCountRequest request, UUID actorId) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + request.getWarehouseId()));

        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_INACTIVE", "Cannot start stock count in an inactive warehouse");
        }

        String countCode = "CNT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        StockCount stockCount = StockCount.builder()
                .countCode(countCode)
                .warehouse(warehouse)
                .status(StockCountStatus.DRAFT)
                .note(request.getNote())
                .createdBy(actorId)
                .build();

        StockCount saved = stockCountRepository.save(stockCount);
        log.info("Created StockCount ID={}, Code={}", saved.getId(), saved.getCountCode());
        auditLogService.log(actorId, "CREATE_STOCK_COUNT", "StockCount", saved.getId().toString(), null, "Created: " + countCode);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StockCountResponse getStockCount(UUID id) {
        StockCount count = stockCountRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found: " + id));
        return mapToResponse(count);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockCountResponse> listStockCounts(UUID warehouseId, StockCountStatus status, Pageable pageable) {
        return stockCountRepository.findAllWithFilters(warehouseId, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public StockCountResponse startCount(UUID id, UUID actorId) {
        StockCount count = stockCountRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found: " + id));

        if (count.getStatus() != StockCountStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only DRAFT counts can be started");
        }

        // Snapshot current inventory balances as system quantity
        List<InventoryBalance> balances = inventoryBalanceRepository.findByWarehouseId(count.getWarehouse().getId());
        for (InventoryBalance balance : balances) {
            StockCountItem item = StockCountItem.builder()
                    .material(balance.getMaterial())
                    .systemQuantity(balance.getOnHandGram())
                    .build();
            count.addItem(item);
        }

        count.setStatus(StockCountStatus.COUNTING);
        count.setStartedAt(Instant.now());
        StockCount saved = stockCountRepository.save(count);

        log.info("Started StockCount ID={}, snapshotted {} items", saved.getId(), balances.size());
        auditLogService.log(actorId, "START_STOCK_COUNT", "StockCount", saved.getId().toString(), "DRAFT", "COUNTING");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public StockCountResponse recordCounts(UUID id, UpdateStockCountItemsRequest request, UUID actorId) {
        StockCount count = stockCountRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found: " + id));

        if (count.getStatus() != StockCountStatus.COUNTING && count.getStatus() != StockCountStatus.REVIEW) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Counts can only be recorded while COUNTING");
        }

        for (UpdateStockCountItemsRequest.CountItemUpdate update : request.getItems()) {
            count.getItems().stream()
                    .filter(i -> i.getMaterial().getId().equals(update.getMaterialId()))
                    .findFirst()
                    .ifPresent(item -> {
                        item.setCountedQuantity(update.getCountedQuantity());
                        item.setVariance(update.getCountedQuantity().subtract(item.getSystemQuantity()));
                        if (update.getNote() != null) item.setNote(update.getNote());
                    });
        }

        count.setStatus(StockCountStatus.REVIEW);
        StockCount saved = stockCountRepository.save(count);
        auditLogService.log(actorId, "RECORD_STOCK_COUNTS", "StockCount", saved.getId().toString(), null, "Updated item counts");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public StockCountResponse completeCount(UUID id, UUID actorId) {
        StockCount count = stockCountRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock count not found: " + id));

        if (count.getStatus() != StockCountStatus.REVIEW && count.getStatus() != StockCountStatus.COUNTING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only counts in REVIEW or COUNTING can be completed");
        }

        // For any variance, automatically create StockAdjustment draft
        for (StockCountItem item : count.getItems()) {
            if (item.getVariance() != null && item.getVariance().compareTo(BigDecimal.ZERO) != 0) {
                String adjCode = "ADJ-CNT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                StockAdjustment adj = StockAdjustment.builder()
                        .adjustmentCode(adjCode)
                        .warehouse(count.getWarehouse())
                        .material(item.getMaterial())
                        .currentQuantity(item.getSystemQuantity())
                        .actualQuantity(item.getCountedQuantity())
                        .difference(item.getVariance())
                        .reason("Auto-generated from Stock Count " + count.getCountCode() + (item.getNote() != null ? ": " + item.getNote() : ""))
                        .status(StockAdjustmentStatus.DRAFT)
                        .requestedBy(actorId)
                        .build();
                stockAdjustmentRepository.save(adj);
                log.info("Auto-generated StockAdjustment for material {}: Variance={}", item.getMaterial().getSku(), item.getVariance());
            }
        }

        count.setStatus(StockCountStatus.COMPLETED);
        count.setCompletedAt(Instant.now());
        StockCount saved = stockCountRepository.save(count);

        log.info("Completed StockCount ID={}, Code={}", saved.getId(), saved.getCountCode());
        auditLogService.log(actorId, "COMPLETE_STOCK_COUNT", "StockCount", saved.getId().toString(), null, "COMPLETED");

        return mapToResponse(saved);
    }

    private StockCountResponse mapToResponse(StockCount count) {
        return StockCountResponse.builder()
                .id(count.getId())
                .countCode(count.getCountCode())
                .warehouseId(count.getWarehouse().getId())
                .warehouseName(count.getWarehouse().getName())
                .warehouseCode(count.getWarehouse().getCode())
                .status(count.getStatus())
                .note(count.getNote())
                .items(count.getItems() != null ? count.getItems().stream().map(i -> StockCountResponse.StockCountItemResponse.builder()
                        .id(i.getId())
                        .materialId(i.getMaterial().getId())
                        .materialName(i.getMaterial().getName())
                        .materialSku(i.getMaterial().getSku())
                        .systemQuantity(i.getSystemQuantity())
                        .countedQuantity(i.getCountedQuantity())
                        .variance(i.getVariance())
                        .note(i.getNote())
                        .build()).toList() : null)
                .startedAt(count.getStartedAt())
                .completedAt(count.getCompletedAt())
                .createdBy(count.getCreatedBy())
                .createdAt(count.getCreatedAt())
                .updatedAt(count.getUpdatedAt())
                .build();
    }
}

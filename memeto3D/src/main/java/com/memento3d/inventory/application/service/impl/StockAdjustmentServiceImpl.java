package com.memento3d.inventory.application.service.impl;

import com.memento3d.inventory.api.request.CreateStockAdjustmentRequest;
import com.memento3d.inventory.api.response.StockAdjustmentResponse;
import com.memento3d.inventory.application.service.StockAdjustmentService;
import com.memento3d.inventory.domain.model.*;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockAdjustmentRepository;
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
public class StockAdjustmentServiceImpl implements StockAdjustmentService {

    private final StockAdjustmentRepository adjustmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public StockAdjustmentResponse createAdjustment(CreateStockAdjustmentRequest request, UUID actorId) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + request.getWarehouseId()));
        Material material = materialRepository.findById(request.getMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + request.getMaterialId()));

        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_INACTIVE", "Cannot adjust stock in an inactive warehouse");
        }

        InventoryBalance balance = inventoryBalanceRepository
                .findByWarehouseIdAndMaterialId(warehouse.getId(), material.getId())
                .orElseGet(() -> InventoryBalance.builder()
                        .warehouse(warehouse)
                        .material(material)
                        .onHandGram(BigDecimal.ZERO)
                        .reservedGram(BigDecimal.ZERO)
                        .minimumStock(material.getMinimumStock() != null ? material.getMinimumStock() : new BigDecimal("500.00"))
                        .build());

        BigDecimal currentQty = balance.getOnHandGram();
        BigDecimal actualQty = request.getActualQuantity();
        BigDecimal difference = actualQty.subtract(currentQty);

        String adjCode = "ADJ-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        StockAdjustment adjustment = StockAdjustment.builder()
                .adjustmentCode(adjCode)
                .warehouse(warehouse)
                .material(material)
                .currentQuantity(currentQty)
                .actualQuantity(actualQty)
                .difference(difference)
                .reason(request.getReason())
                .evidence(request.getEvidence())
                .status(StockAdjustmentStatus.DRAFT)
                .requestedBy(actorId)
                .build();

        StockAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("Created StockAdjustment ID={}, Code={}, Diff={}", saved.getId(), saved.getAdjustmentCode(), difference);
        auditLogService.log(actorId, "CREATE_ADJUSTMENT", "StockAdjustment", saved.getId().toString(), null, "Created: " + adjCode + ", Diff: " + difference);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StockAdjustmentResponse getAdjustment(UUID id) {
        StockAdjustment adjustment = adjustmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock adjustment not found: " + id));
        return mapToResponse(adjustment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockAdjustmentResponse> listAdjustments(UUID warehouseId, UUID materialId, StockAdjustmentStatus status, Pageable pageable) {
        return adjustmentRepository.findAllWithFilters(warehouseId, materialId, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public StockAdjustmentResponse submitAdjustment(UUID id, UUID actorId) {
        StockAdjustment adjustment = adjustmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock adjustment not found: " + id));

        if (adjustment.getStatus() != StockAdjustmentStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only DRAFT adjustments can be submitted");
        }

        adjustment.setStatus(StockAdjustmentStatus.SUBMITTED);
        StockAdjustment saved = adjustmentRepository.save(adjustment);
        auditLogService.log(actorId, "SUBMIT_ADJUSTMENT", "StockAdjustment", saved.getId().toString(), "DRAFT", "SUBMITTED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public StockAdjustmentResponse approveAdjustment(UUID id, UUID actorId) {
        StockAdjustment adjustment = adjustmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock adjustment not found: " + id));

        if (adjustment.getStatus() != StockAdjustmentStatus.SUBMITTED && adjustment.getStatus() != StockAdjustmentStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only SUBMITTED adjustments can be approved");
        }

        adjustment.setStatus(StockAdjustmentStatus.APPROVED);
        adjustment.setApprovedBy(actorId);
        StockAdjustment saved = adjustmentRepository.save(adjustment);
        auditLogService.log(actorId, "APPROVE_ADJUSTMENT", "StockAdjustment", saved.getId().toString(), "SUBMITTED", "APPROVED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public StockAdjustmentResponse rejectAdjustment(UUID id, String reason, UUID actorId) {
        StockAdjustment adjustment = adjustmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock adjustment not found: " + id));

        if (adjustment.getStatus() == StockAdjustmentStatus.APPLIED) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_REJECT_APPLIED", "Cannot reject an already applied adjustment");
        }

        adjustment.setStatus(StockAdjustmentStatus.REJECTED);
        adjustment.setApprovedBy(actorId);
        StockAdjustment saved = adjustmentRepository.save(adjustment);
        auditLogService.log(actorId, "REJECT_ADJUSTMENT", "StockAdjustment", saved.getId().toString(), null, "Rejected: " + reason);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public StockAdjustmentResponse applyAdjustment(UUID id, UUID actorId) {
        StockAdjustment adjustment = adjustmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock adjustment not found: " + id));

        if (adjustment.getStatus() == StockAdjustmentStatus.APPLIED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ALREADY_APPLIED", "This stock adjustment has already been applied");
        }

        if (adjustment.getStatus() != StockAdjustmentStatus.APPROVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only APPROVED adjustments can be applied");
        }

        Warehouse warehouse = adjustment.getWarehouse();
        Material material = adjustment.getMaterial();

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
        BigDecimal afterOnHand = adjustment.getActualQuantity();
        BigDecimal diff = afterOnHand.subtract(beforeOnHand);
        BigDecimal beforeReserved = balance.getReservedGram();

        if (afterOnHand.compareTo(beforeReserved) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ADJUSTMENT_BELOW_RESERVED",
                    "Cannot set stock level below current reserved quantity: " + beforeReserved + " g");
        }

        balance.setOnHandGram(afterOnHand);
        inventoryBalanceRepository.save(balance);

        InventoryTransactionType txType = diff.compareTo(BigDecimal.ZERO) >= 0
                ? InventoryTransactionType.ADJUST_INCREASE
                : InventoryTransactionType.ADJUST_DECREASE;

        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(txType)
                .warehouse(warehouse)
                .material(material)
                .deltaOnHandGram(diff)
                .deltaReservedGram(BigDecimal.ZERO)
                .beforeOnHandGram(beforeOnHand)
                .afterOnHandGram(afterOnHand)
                .beforeReservedGram(beforeReserved)
                .afterReservedGram(beforeReserved)
                .referenceId(adjustment.getAdjustmentCode())
                .reason("Adjustment: " + adjustment.getReason())
                .performedBy(actorId)
                .approvedBy(adjustment.getApprovedBy())
                .build();
        inventoryTransactionRepository.save(tx);

        adjustment.setStatus(StockAdjustmentStatus.APPLIED);
        adjustment.setAppliedAt(Instant.now());
        StockAdjustment saved = adjustmentRepository.save(adjustment);

        log.info("Applied StockAdjustment ID={}, Code={}, Delta={}", saved.getId(), saved.getAdjustmentCode(), diff);
        auditLogService.log(actorId, "APPLY_ADJUSTMENT", "StockAdjustment", saved.getId().toString(), "APPROVED", "APPLIED");

        return mapToResponse(saved);
    }

    private StockAdjustmentResponse mapToResponse(StockAdjustment adj) {
        return StockAdjustmentResponse.builder()
                .id(adj.getId())
                .adjustmentCode(adj.getAdjustmentCode())
                .warehouseId(adj.getWarehouse().getId())
                .warehouseName(adj.getWarehouse().getName())
                .warehouseCode(adj.getWarehouse().getCode())
                .materialId(adj.getMaterial().getId())
                .materialName(adj.getMaterial().getName())
                .materialSku(adj.getMaterial().getSku())
                .currentQuantity(adj.getCurrentQuantity())
                .actualQuantity(adj.getActualQuantity())
                .difference(adj.getDifference())
                .reason(adj.getReason())
                .evidence(adj.getEvidence())
                .status(adj.getStatus())
                .requestedBy(adj.getRequestedBy())
                .approvedBy(adj.getApprovedBy())
                .appliedAt(adj.getAppliedAt())
                .createdAt(adj.getCreatedAt())
                .updatedAt(adj.getUpdatedAt())
                .build();
    }
}

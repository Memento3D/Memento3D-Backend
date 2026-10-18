package com.memento3d.inventory.application.service.impl;

import com.memento3d.inventory.api.request.CreateStockIssueRequest;
import com.memento3d.inventory.api.request.CreateStockReceiptRequest;
import com.memento3d.inventory.api.response.InventoryBalanceResponse;
import com.memento3d.inventory.api.response.InventoryTransactionResponse;
import com.memento3d.inventory.api.response.StockIssueResponse;
import com.memento3d.inventory.api.response.StockReceiptResponse;
import com.memento3d.inventory.application.service.InventoryService;
import com.memento3d.inventory.domain.model.*;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockIssueRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockReceiptRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryBalanceRepository balanceRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockIssueRepository issueRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryBalanceResponse> listBalances(UUID warehouseId, UUID materialId, Pageable pageable) {
        if (warehouseId == null && materialId == null) {
            return balanceRepository.findAll(pageable).map(InventoryBalanceResponse::fromEntity);
        }
        return balanceRepository.findAllWithFilters(warehouseId, materialId, pageable)
                .map(InventoryBalanceResponse::fromEntity);
    }

    @Override
    @Transactional
    public StockReceiptResponse createReceipt(CreateStockReceiptRequest request, UUID actorId) {
        // Idempotency check
        String idempotencyKey = request.getIdempotencyKey().trim();
        Optional<StockReceipt> existing = receiptRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent receipt request returning existing receipt ID={}", existing.get().getId());
            return StockReceiptResponse.fromEntity(existing.get());
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.getWarehouseId()));
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "WAREHOUSE_INACTIVE", "Cannot receive stock into inactive warehouse");
        }

        Material material = materialRepository.findById(request.getMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + request.getMaterialId()));
        if (material.getStatus() != MaterialStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATERIAL_INACTIVE", "Cannot receive stock for inactive material");
        }

        BigDecimal qty = request.getQuantityGram();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Receipt quantity must be positive");
        }

        // Lock or initialize inventory balance
        InventoryBalance balance = balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouse.getId(), material.getId())
                .orElseGet(() -> {
                    InventoryBalance newBalance = InventoryBalance.builder()
                            .warehouse(warehouse)
                            .material(material)
                            .onHandGram(BigDecimal.ZERO)
                            .reservedGram(BigDecimal.ZERO)
                            .build();
                    return balanceRepository.save(newBalance);
                });

        BigDecimal beforeOnHand = balance.getOnHandGram();
        BigDecimal afterOnHand = beforeOnHand.add(qty);
        BigDecimal beforeReserved = balance.getReservedGram();

        balance.setOnHandGram(afterOnHand);
        balanceRepository.save(balance);

        String receiptNumber = "REC-" + Instant.now().toEpochMilli();
        StockReceipt receipt = StockReceipt.builder()
                .receiptNumber(receiptNumber)
                .warehouse(warehouse)
                .material(material)
                .quantityGram(qty)
                .unitCost(request.getUnitCost())
                .supplier(request.getSupplier())
                .batchNumber(request.getBatchNumber())
                .idempotencyKey(idempotencyKey)
                .note(request.getNote())
                .receivedBy(actorId)
                .build();

        StockReceipt savedReceipt = receiptRepository.save(receipt);

        // Immutable transaction ledger
        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(InventoryTransactionType.RECEIPT)
                .warehouse(warehouse)
                .material(material)
                .deltaOnHandGram(qty)
                .deltaReservedGram(BigDecimal.ZERO)
                .beforeOnHandGram(beforeOnHand)
                .afterOnHandGram(afterOnHand)
                .beforeReservedGram(beforeReserved)
                .afterReservedGram(beforeReserved)
                .referenceId(receiptNumber)
                .idempotencyKey(idempotencyKey)
                .performedBy(actorId)
                .build();
        transactionRepository.save(tx);

        log.info("Stock in successful: Receipt={}, Material={}, Qty={}g, NewOnHand={}g",
                receiptNumber, material.getSku(), qty, afterOnHand);

        return StockReceiptResponse.fromEntity(savedReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockReceiptResponse> listReceipts(UUID warehouseId, UUID materialId, Pageable pageable) {
        if (warehouseId == null && materialId == null) {
            return receiptRepository.findAll(pageable).map(StockReceiptResponse::fromEntity);
        }
        return receiptRepository.findAllWithFilters(warehouseId, materialId, pageable)
                .map(StockReceiptResponse::fromEntity);
    }

    @Override
    @Transactional
    public StockIssueResponse createIssue(CreateStockIssueRequest request, UUID actorId) {
        String idempotencyKey = request.getIdempotencyKey().trim();
        Optional<StockIssue> existing = issueRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent issue request returning existing issue ID={}", existing.get().getId());
            return StockIssueResponse.fromEntity(existing.get());
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.getWarehouseId()));
        Material material = materialRepository.findById(request.getMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + request.getMaterialId()));

        BigDecimal qty = request.getQuantityGram();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Issue quantity must be positive");
        }

        // Lock row with PESSIMISTIC_WRITE
        InventoryBalance balance = balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouse.getId(), material.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                        "No inventory balance exists for this material in warehouse"));

        BigDecimal available = balance.getAvailableGram();
        if (available.compareTo(qty) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Insufficient available stock: Requested " + qty + "g, Available " + available + "g");
        }

        BigDecimal beforeOnHand = balance.getOnHandGram();
        BigDecimal afterOnHand = beforeOnHand.subtract(qty);
        BigDecimal beforeReserved = balance.getReservedGram();

        balance.setOnHandGram(afterOnHand);
        balanceRepository.save(balance);

        String issueNumber = "ISS-" + Instant.now().toEpochMilli();
        StockIssue issue = StockIssue.builder()
                .issueNumber(issueNumber)
                .warehouse(warehouse)
                .material(material)
                .quantityGram(qty)
                .reason(request.getReason())
                .idempotencyKey(idempotencyKey)
                .note(request.getNote())
                .issuedBy(actorId)
                .build();
        StockIssue savedIssue = issueRepository.save(issue);

        // Immutable transaction ledger
        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(InventoryTransactionType.ISSUE)
                .warehouse(warehouse)
                .material(material)
                .deltaOnHandGram(qty.negate())
                .deltaReservedGram(BigDecimal.ZERO)
                .beforeOnHandGram(beforeOnHand)
                .afterOnHandGram(afterOnHand)
                .beforeReservedGram(beforeReserved)
                .afterReservedGram(beforeReserved)
                .referenceId(issueNumber)
                .idempotencyKey(idempotencyKey)
                .performedBy(actorId)
                .build();
        transactionRepository.save(tx);

        log.info("Stock out successful: Issue={}, Material={}, Qty={}g, NewOnHand={}g",
                issueNumber, material.getSku(), qty, afterOnHand);

        return StockIssueResponse.fromEntity(savedIssue);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockIssueResponse> listIssues(UUID warehouseId, UUID materialId, Pageable pageable) {
        if (warehouseId == null && materialId == null) {
            return issueRepository.findAll(pageable).map(StockIssueResponse::fromEntity);
        }
        return issueRepository.findAllWithFilters(warehouseId, materialId, pageable)
                .map(StockIssueResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> listTransactions(
            UUID warehouseId,
            UUID materialId,
            InventoryTransactionType type,
            UUID printJobId,
            Pageable pageable
    ) {
        if (warehouseId == null && materialId == null && type == null && printJobId == null) {
            return transactionRepository.findAll(pageable).map(InventoryTransactionResponse::fromEntity);
        }
        return transactionRepository.findAllWithFilters(warehouseId, materialId, type, printJobId, pageable)
                .map(InventoryTransactionResponse::fromEntity);
    }
}

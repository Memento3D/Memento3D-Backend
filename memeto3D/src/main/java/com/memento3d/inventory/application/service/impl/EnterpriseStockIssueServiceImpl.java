package com.memento3d.inventory.application.service.impl;

import com.memento3d.inventory.api.request.CreateEnterpriseStockIssueRequest;
import com.memento3d.inventory.api.response.EnterpriseStockIssueResponse;
import com.memento3d.inventory.application.service.EnterpriseStockIssueService;
import com.memento3d.inventory.domain.model.*;
import com.memento3d.inventory.infrastructure.persistence.repository.EnterpriseStockIssueItemRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.EnterpriseStockIssueRepository;
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
public class EnterpriseStockIssueServiceImpl implements EnterpriseStockIssueService {

    private final EnterpriseStockIssueRepository issueRepository;
    private final EnterpriseStockIssueItemRepository issueItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public EnterpriseStockIssueResponse createIssue(CreateEnterpriseStockIssueRequest request, UUID actorId) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + request.getWarehouseId()));

        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_INACTIVE",
                    "Cannot issue stock from inactive or maintenance warehouse: " + warehouse.getCode());
        }

        String issueCode = "ISS-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        EnterpriseStockIssue issue = EnterpriseStockIssue.builder()
                .issueCode(issueCode)
                .warehouse(warehouse)
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .reason(request.getReason())
                .status(EnterpriseStockIssueStatus.DRAFT)
                .note(request.getNote())
                .createdBy(actorId)
                .build();

        for (CreateEnterpriseStockIssueRequest.EnterpriseStockIssueItemRequest itemReq : request.getItems()) {
            Material material = materialRepository.findById(itemReq.getMaterialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + itemReq.getMaterialId()));

            EnterpriseStockIssueItem item = EnterpriseStockIssueItem.builder()
                    .material(material)
                    .requestedQuantity(itemReq.getRequestedQuantity())
                    .issuedQuantity(BigDecimal.ZERO)
                    .note(itemReq.getNote())
                    .build();
            issue.addItem(item);
        }

        EnterpriseStockIssue saved = issueRepository.save(issue);
        log.info("Created EnterpriseStockIssue ID={}, Code={}", saved.getId(), saved.getIssueCode());
        auditLogService.log(actorId, "CREATE_STOCK_ISSUE", "StockIssue", saved.getId().toString(), null, "Created: " + issueCode);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EnterpriseStockIssueResponse getIssue(UUID id) {
        EnterpriseStockIssue issue = issueRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock issue not found: " + id));
        return mapToResponse(issue);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnterpriseStockIssueResponse> listIssues(UUID warehouseId, EnterpriseStockIssueStatus status, Pageable pageable) {
        return issueRepository.findAllWithFilters(warehouseId, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public EnterpriseStockIssueResponse submitIssue(UUID id, UUID actorId) {
        EnterpriseStockIssue issue = issueRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock issue not found: " + id));

        if (issue.getStatus() != EnterpriseStockIssueStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only DRAFT issues can be submitted");
        }

        issue.setStatus(EnterpriseStockIssueStatus.SUBMITTED);
        EnterpriseStockIssue saved = issueRepository.save(issue);
        auditLogService.log(actorId, "SUBMIT_STOCK_ISSUE", "StockIssue", saved.getId().toString(), "DRAFT", "SUBMITTED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public EnterpriseStockIssueResponse approveIssue(UUID id, UUID actorId) {
        EnterpriseStockIssue issue = issueRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock issue not found: " + id));

        if (issue.getStatus() != EnterpriseStockIssueStatus.SUBMITTED && issue.getStatus() != EnterpriseStockIssueStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only SUBMITTED issues can be approved");
        }

        issue.setStatus(EnterpriseStockIssueStatus.APPROVED);
        issue.setApprovedBy(actorId);
        EnterpriseStockIssue saved = issueRepository.save(issue);
        auditLogService.log(actorId, "APPROVE_STOCK_ISSUE", "StockIssue", saved.getId().toString(), "SUBMITTED", "APPROVED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public EnterpriseStockIssueResponse completeIssue(UUID id, UUID actorId) {
        EnterpriseStockIssue issue = issueRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock issue not found: " + id));

        if (issue.getStatus() == EnterpriseStockIssueStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "ISSUE_ALREADY_COMPLETED", "This stock issue is already completed");
        }

        if (issue.getStatus() != EnterpriseStockIssueStatus.APPROVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only APPROVED stock issues can be completed");
        }

        Warehouse warehouse = issue.getWarehouse();
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_INACTIVE", "Cannot issue stock from an inactive warehouse");
        }

        // Validate all items first
        for (EnterpriseStockIssueItem item : issue.getItems()) {
            Material material = item.getMaterial();
            BigDecimal reqQty = item.getRequestedQuantity();

            InventoryBalance balance = inventoryBalanceRepository
                    .findByWarehouseIdAndMaterialIdForUpdate(warehouse.getId(), material.getId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVENTORY_NOT_FOUND",
                            "No inventory balance found for material: " + material.getName()));

            if (balance.getAvailableGram().compareTo(reqQty) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                        String.format("Insufficient available stock for %s. Available: %s g, Requested: %s g",
                                material.getName(), balance.getAvailableGram(), reqQty));
            }

            BigDecimal beforeOnHand = balance.getOnHandGram();
            BigDecimal afterOnHand = beforeOnHand.subtract(reqQty);
            BigDecimal beforeReserved = balance.getReservedGram();

            balance.setOnHandGram(afterOnHand);
            inventoryBalanceRepository.save(balance);

            item.setIssuedQuantity(reqQty);

            InventoryTransaction tx = InventoryTransaction.builder()
                    .transactionType(InventoryTransactionType.STOCK_OUT)
                    .warehouse(warehouse)
                    .material(material)
                    .deltaOnHandGram(reqQty.negate())
                    .deltaReservedGram(BigDecimal.ZERO)
                    .beforeOnHandGram(beforeOnHand)
                    .afterOnHandGram(afterOnHand)
                    .beforeReservedGram(beforeReserved)
                    .afterReservedGram(beforeReserved)
                    .referenceId(issue.getIssueCode())
                    .reason("Stock Issue: " + issue.getReason())
                    .performedBy(actorId)
                    .approvedBy(issue.getApprovedBy())
                    .build();
            inventoryTransactionRepository.save(tx);
        }

        issue.setStatus(EnterpriseStockIssueStatus.COMPLETED);
        issue.setCompletedAt(Instant.now());
        EnterpriseStockIssue saved = issueRepository.save(issue);

        log.info("Completed EnterpriseStockIssue ID={}, Code={}", saved.getId(), saved.getIssueCode());
        auditLogService.log(actorId, "COMPLETE_STOCK_ISSUE", "StockIssue", saved.getId().toString(), "APPROVED", "COMPLETED");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public EnterpriseStockIssueResponse cancelIssue(UUID id, UUID actorId) {
        EnterpriseStockIssue issue = issueRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock issue not found: " + id));

        if (issue.getStatus() == EnterpriseStockIssueStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_CANCEL_COMPLETED", "Completed stock issues cannot be cancelled.");
        }

        issue.setStatus(EnterpriseStockIssueStatus.CANCELLED);
        EnterpriseStockIssue saved = issueRepository.save(issue);
        auditLogService.log(actorId, "CANCEL_STOCK_ISSUE", "StockIssue", saved.getId().toString(), null, "CANCELLED");
        return mapToResponse(saved);
    }

    private EnterpriseStockIssueResponse mapToResponse(EnterpriseStockIssue issue) {
        return EnterpriseStockIssueResponse.builder()
                .id(issue.getId())
                .issueCode(issue.getIssueCode())
                .warehouseId(issue.getWarehouse().getId())
                .warehouseName(issue.getWarehouse().getName())
                .warehouseCode(issue.getWarehouse().getCode())
                .referenceType(issue.getReferenceType())
                .referenceId(issue.getReferenceId())
                .reason(issue.getReason())
                .status(issue.getStatus())
                .note(issue.getNote())
                .items(issue.getItems() != null ? issue.getItems().stream().map(i -> EnterpriseStockIssueResponse.EnterpriseStockIssueItemResponse.builder()
                        .id(i.getId())
                        .materialId(i.getMaterial().getId())
                        .materialName(i.getMaterial().getName())
                        .materialSku(i.getMaterial().getSku())
                        .requestedQuantity(i.getRequestedQuantity())
                        .issuedQuantity(i.getIssuedQuantity())
                        .note(i.getNote())
                        .build()).toList() : null)
                .createdBy(issue.getCreatedBy())
                .approvedBy(issue.getApprovedBy())
                .completedAt(issue.getCompletedAt())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .build();
    }
}

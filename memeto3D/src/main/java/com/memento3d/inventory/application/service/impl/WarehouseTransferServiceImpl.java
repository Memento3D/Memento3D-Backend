package com.memento3d.inventory.application.service.impl;

import com.memento3d.inventory.api.request.CreateWarehouseTransferRequest;
import com.memento3d.inventory.api.response.WarehouseTransferResponse;
import com.memento3d.inventory.application.service.WarehouseTransferService;
import com.memento3d.inventory.domain.model.*;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.WarehouseTransferItemRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.WarehouseTransferRepository;
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
public class WarehouseTransferServiceImpl implements WarehouseTransferService {

    private final WarehouseTransferRepository transferRepository;
    private final WarehouseTransferItemRepository transferItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public WarehouseTransferResponse createTransfer(CreateWarehouseTransferRequest request, UUID actorId) {
        if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IDENTICAL_WAREHOUSES", "Source and destination warehouses must be different");
        }

        Warehouse source = warehouseRepository.findById(request.getSourceWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Source warehouse not found: " + request.getSourceWarehouseId()));
        Warehouse destination = warehouseRepository.findById(request.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination warehouse not found: " + request.getDestinationWarehouseId()));

        if (source.getStatus() != WarehouseStatus.ACTIVE || destination.getStatus() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_INACTIVE", "Both warehouses must be ACTIVE to transfer stock");
        }

        String transferCode = "TRF-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        WarehouseTransfer transfer = WarehouseTransfer.builder()
                .transferCode(transferCode)
                .sourceWarehouse(source)
                .destinationWarehouse(destination)
                .status(WarehouseTransferStatus.DRAFT)
                .note(request.getNote())
                .createdBy(actorId)
                .build();

        for (CreateWarehouseTransferRequest.TransferItemRequest itemReq : request.getItems()) {
            Material material = materialRepository.findById(itemReq.getMaterialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + itemReq.getMaterialId()));

            WarehouseTransferItem item = WarehouseTransferItem.builder()
                    .material(material)
                    .quantity(itemReq.getQuantity())
                    .note(itemReq.getNote())
                    .build();
            transfer.addItem(item);
        }

        WarehouseTransfer saved = transferRepository.save(transfer);
        log.info("Created WarehouseTransfer ID={}, Code={}", saved.getId(), saved.getTransferCode());
        auditLogService.log(actorId, "CREATE_TRANSFER", "WarehouseTransfer", saved.getId().toString(), null, "Created: " + transferCode);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseTransferResponse getTransfer(UUID id) {
        WarehouseTransfer transfer = transferRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));
        return mapToResponse(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WarehouseTransferResponse> listTransfers(UUID warehouseId, WarehouseTransferStatus status, Pageable pageable) {
        return transferRepository.findAllWithFilters(warehouseId, status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public WarehouseTransferResponse submitTransfer(UUID id, UUID actorId) {
        WarehouseTransfer transfer = transferRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));

        if (transfer.getStatus() != WarehouseTransferStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only DRAFT transfers can be submitted");
        }

        transfer.setStatus(WarehouseTransferStatus.SUBMITTED);
        WarehouseTransfer saved = transferRepository.save(transfer);
        auditLogService.log(actorId, "SUBMIT_TRANSFER", "WarehouseTransfer", saved.getId().toString(), "DRAFT", "SUBMITTED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseTransferResponse approveTransfer(UUID id, UUID actorId) {
        WarehouseTransfer transfer = transferRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));

        if (transfer.getStatus() != WarehouseTransferStatus.SUBMITTED && transfer.getStatus() != WarehouseTransferStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only SUBMITTED transfers can be approved");
        }

        transfer.setStatus(WarehouseTransferStatus.APPROVED);
        transfer.setApprovedBy(actorId);
        WarehouseTransfer saved = transferRepository.save(transfer);
        auditLogService.log(actorId, "APPROVE_TRANSFER", "WarehouseTransfer", saved.getId().toString(), "SUBMITTED", "APPROVED");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseTransferResponse shipTransfer(UUID id, UUID actorId) {
        WarehouseTransfer transfer = transferRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));

        if (transfer.getStatus() != WarehouseTransferStatus.APPROVED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only APPROVED transfers can be shipped");
        }

        Warehouse source = transfer.getSourceWarehouse();

        // Validate source warehouse stock and deduct
        for (WarehouseTransferItem item : transfer.getItems()) {
            Material material = item.getMaterial();
            BigDecimal qty = item.getQuantity();

            InventoryBalance balance = inventoryBalanceRepository
                    .findByWarehouseIdAndMaterialIdForUpdate(source.getId(), material.getId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                            "Source warehouse has no stock for " + material.getName()));

            if (balance.getAvailableGram().compareTo(qty) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                        String.format("Insufficient stock in %s for %s. Available: %s g, Transfer: %s g",
                                source.getName(), material.getName(), balance.getAvailableGram(), qty));
            }

            BigDecimal beforeOnHand = balance.getOnHandGram();
            BigDecimal afterOnHand = beforeOnHand.subtract(qty);
            BigDecimal beforeReserved = balance.getReservedGram();

            balance.setOnHandGram(afterOnHand);
            inventoryBalanceRepository.save(balance);

            InventoryTransaction tx = InventoryTransaction.builder()
                    .transactionType(InventoryTransactionType.TRANSFER_OUT)
                    .warehouse(source)
                    .material(material)
                    .deltaOnHandGram(qty.negate())
                    .deltaReservedGram(BigDecimal.ZERO)
                    .beforeOnHandGram(beforeOnHand)
                    .afterOnHandGram(afterOnHand)
                    .beforeReservedGram(beforeReserved)
                    .afterReservedGram(beforeReserved)
                    .referenceId(transfer.getTransferCode())
                    .reason("Transfer to " + transfer.getDestinationWarehouse().getCode())
                    .performedBy(actorId)
                    .approvedBy(transfer.getApprovedBy())
                    .build();
            inventoryTransactionRepository.save(tx);
        }

        transfer.setStatus(WarehouseTransferStatus.IN_TRANSIT);
        transfer.setShippedAt(Instant.now());
        WarehouseTransfer saved = transferRepository.save(transfer);

        log.info("Shipped WarehouseTransfer ID={}, Code={}", saved.getId(), saved.getTransferCode());
        auditLogService.log(actorId, "SHIP_TRANSFER", "WarehouseTransfer", saved.getId().toString(), "APPROVED", "IN_TRANSIT");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseTransferResponse receiveTransfer(UUID id, UUID actorId) {
        WarehouseTransfer transfer = transferRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));

        if (transfer.getStatus() == WarehouseTransferStatus.RECEIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRANSFER_ALREADY_RECEIVED", "This transfer has already been received");
        }

        if (transfer.getStatus() != WarehouseTransferStatus.IN_TRANSIT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Only IN_TRANSIT transfers can be received");
        }

        Warehouse destination = transfer.getDestinationWarehouse();

        // Increment stock in destination warehouse
        for (WarehouseTransferItem item : transfer.getItems()) {
            Material material = item.getMaterial();
            BigDecimal qty = item.getQuantity();

            InventoryBalance balance = inventoryBalanceRepository
                    .findByWarehouseIdAndMaterialIdForUpdate(destination.getId(), material.getId())
                    .orElseGet(() -> {
                        InventoryBalance newBalance = InventoryBalance.builder()
                                .warehouse(destination)
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
                    .transactionType(InventoryTransactionType.TRANSFER_IN)
                    .warehouse(destination)
                    .material(material)
                    .deltaOnHandGram(qty)
                    .deltaReservedGram(BigDecimal.ZERO)
                    .beforeOnHandGram(beforeOnHand)
                    .afterOnHandGram(afterOnHand)
                    .beforeReservedGram(beforeReserved)
                    .afterReservedGram(beforeReserved)
                    .referenceId(transfer.getTransferCode())
                    .reason("Received transfer from " + transfer.getSourceWarehouse().getCode())
                    .performedBy(actorId)
                    .approvedBy(transfer.getApprovedBy())
                    .build();
            inventoryTransactionRepository.save(tx);
        }

        transfer.setStatus(WarehouseTransferStatus.RECEIVED);
        transfer.setReceivedAt(Instant.now());
        WarehouseTransfer saved = transferRepository.save(transfer);

        log.info("Received WarehouseTransfer ID={}, Code={}", saved.getId(), saved.getTransferCode());
        auditLogService.log(actorId, "RECEIVE_TRANSFER", "WarehouseTransfer", saved.getId().toString(), "IN_TRANSIT", "RECEIVED");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseTransferResponse cancelTransfer(UUID id, UUID actorId) {
        WarehouseTransfer transfer = transferRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found: " + id));

        if (transfer.getStatus() == WarehouseTransferStatus.IN_TRANSIT || transfer.getStatus() == WarehouseTransferStatus.RECEIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_CANCEL_SHIPPED", "Cannot cancel transfer that has already been shipped or received");
        }

        transfer.setStatus(WarehouseTransferStatus.CANCELLED);
        WarehouseTransfer saved = transferRepository.save(transfer);
        auditLogService.log(actorId, "CANCEL_TRANSFER", "WarehouseTransfer", saved.getId().toString(), null, "CANCELLED");
        return mapToResponse(saved);
    }

    private WarehouseTransferResponse mapToResponse(WarehouseTransfer transfer) {
        boolean itemsInitialized = transfer.getItems() != null && org.hibernate.Hibernate.isInitialized(transfer.getItems());
        return WarehouseTransferResponse.builder()
                .id(transfer.getId())
                .transferCode(transfer.getTransferCode())
                .sourceWarehouseId(transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getId() : null)
                .sourceWarehouseName(transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : null)
                .sourceWarehouseCode(transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getCode() : null)
                .destinationWarehouseId(transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getId() : null)
                .destinationWarehouseName(transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getName() : null)
                .destinationWarehouseCode(transfer.getDestinationWarehouse() != null ? transfer.getDestinationWarehouse().getCode() : null)
                .status(transfer.getStatus())
                .note(transfer.getNote())
                .itemCount(itemsInitialized ? transfer.getItems().size() : null)
                .items(itemsInitialized ? transfer.getItems().stream().map(i -> WarehouseTransferResponse.TransferItemResponse.builder()
                        .id(i.getId())
                        .materialId(i.getMaterial() != null ? i.getMaterial().getId() : null)
                        .materialName(i.getMaterial() != null ? i.getMaterial().getName() : null)
                        .materialSku(i.getMaterial() != null ? i.getMaterial().getSku() : null)
                        .quantity(i.getQuantity())
                        .note(i.getNote())
                        .build()).toList() : null)
                .createdBy(transfer.getCreatedBy())
                .approvedBy(transfer.getApprovedBy())
                .shippedAt(transfer.getShippedAt())
                .receivedAt(transfer.getReceivedAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }
}

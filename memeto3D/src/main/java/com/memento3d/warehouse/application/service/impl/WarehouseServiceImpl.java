package com.memento3d.warehouse.application.service.impl;

import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.request.UpdateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.application.service.WarehouseService;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request, UUID actorId) {
        String cleanCode = request.getCode().trim().toUpperCase();
        if (warehouseRepository.existsByCodeIgnoreCase(cleanCode)) {
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_WAREHOUSE_CODE",
                    "Warehouse with code '" + cleanCode + "' already exists");
        }

        Warehouse warehouse = Warehouse.builder()
                .code(cleanCode)
                .name(request.getName().trim())
                .location(request.getLocation() != null ? request.getLocation().trim() : null)
                .status(WarehouseStatus.ACTIVE)
                .createdBy(actorId)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Created warehouse ID={}, Code={}", saved.getId(), saved.getCode());
        return WarehouseResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouse(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));
        return WarehouseResponse.fromEntity(warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WarehouseResponse> listWarehouses(WarehouseStatus status, String search, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        if (status == null && cleanSearch == null) {
            return warehouseRepository.findAll(pageable).map(WarehouseResponse::fromEntity);
        }
        return warehouseRepository.findAllWithFilters(status, cleanSearch, pageable)
                .map(WarehouseResponse::fromEntity);
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouse(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (warehouseRepository.existsByCodeIgnoreCaseAndIdNot(newCode, id)) {
                throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_WAREHOUSE_CODE",
                        "Warehouse with code '" + newCode + "' already exists");
            }
            warehouse.setCode(newCode);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            warehouse.setName(request.getName().trim());
        }
        if (request.getLocation() != null) {
            warehouse.setLocation(request.getLocation().trim());
        }
        if (request.getStatus() != null && request.getStatus() != warehouse.getStatus()) {
            if (request.getStatus() == WarehouseStatus.INACTIVE) {
                assertWarehouseCanBeDeactivated(id);
            }
            warehouse.setStatus(request.getStatus());
        }

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Updated warehouse ID={}, Code={}", saved.getId(), saved.getCode());
        return WarehouseResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public WarehouseResponse deactivateWarehouse(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));

        assertWarehouseCanBeDeactivated(id);
        warehouse.setStatus(WarehouseStatus.INACTIVE);
        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Deactivated warehouse ID={}, Code={}", saved.getId(), saved.getCode());
        return WarehouseResponse.fromEntity(saved);
    }

    private void assertWarehouseCanBeDeactivated(UUID warehouseId) {
        if (inventoryBalanceRepository.existsByWarehouseIdAndOnHandGramGreaterThan(warehouseId, BigDecimal.ZERO)) {
            throw new BusinessException(HttpStatus.CONFLICT, "WAREHOUSE_HAS_STOCK",
                    "Cannot deactivate warehouse with active stock on hand");
        }
        if (inventoryBalanceRepository.existsByWarehouseIdAndReservedGramGreaterThan(warehouseId, BigDecimal.ZERO)) {
            throw new BusinessException(HttpStatus.CONFLICT, "WAREHOUSE_HAS_RESERVATIONS",
                    "Cannot deactivate warehouse with active stock reservations");
        }
    }
}

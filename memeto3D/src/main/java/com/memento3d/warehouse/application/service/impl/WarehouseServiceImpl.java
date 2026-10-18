package com.memento3d.warehouse.application.service.impl;

import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.warehouse.api.request.AssignMemberRequest;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.request.UpdateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseMembershipResponse;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.application.service.WarehouseSecurityService;
import com.memento3d.warehouse.application.service.WarehouseService;
import com.memento3d.warehouse.domain.model.MembershipStatus;
import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.domain.model.WarehouseMembership;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseMembershipRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMembershipRepository warehouseMembershipRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final UserRepository userRepository;
    private final WarehouseSecurityService warehouseSecurityService;

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

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getMyWarehouses(UUID userId, String systemRole) {
        if (warehouseSecurityService.isAdmin(systemRole) || warehouseSecurityService.isManager(systemRole)) {
            return warehouseRepository.findAll().stream()
                    .filter(w -> w.getStatus() == WarehouseStatus.ACTIVE)
                    .map(WarehouseResponse::fromEntity)
                    .toList();
        }

        if (userId == null) {
            return List.of();
        }

        List<WarehouseResponse> memberships = warehouseMembershipRepository.findAllByUserIdAndStatusWithWarehouse(userId, MembershipStatus.ACTIVE)
                .stream()
                .map(WarehouseMembership::getWarehouse)
                .filter(w -> w.getStatus() == WarehouseStatus.ACTIVE)
                .map(WarehouseResponse::fromEntity)
                .toList();

        // If no explicit membership is assigned yet, fallback to active warehouses so staff is never locked out of operations
        if (memberships.isEmpty()) {
            return warehouseRepository.findAll().stream()
                    .filter(w -> w.getStatus() == WarehouseStatus.ACTIVE)
                    .map(WarehouseResponse::fromEntity)
                    .toList();
        }

        return memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseMembershipResponse> getWarehouseMembers(UUID warehouseId, UUID actorId, String systemRole) {
        warehouseSecurityService.validateWarehouseAccess(warehouseId, actorId, systemRole);

        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse not found with id: " + warehouseId);
        }

        return warehouseMembershipRepository.findAllByWarehouseIdWithUser(warehouseId)
                .stream()
                .map(WarehouseMembershipResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public WarehouseMembershipResponse assignMember(UUID warehouseId, AssignMemberRequest request, UUID actorId, String systemRole) {
        warehouseSecurityService.validateWarehouseManager(warehouseId, actorId, systemRole);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + warehouseId));

        User targetUser;
        if (request.getUserId() != null) {
            targetUser = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));
        } else if (request.getEmail() != null && !request.getEmail().isBlank()) {
            targetUser = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "USER_REQUIRED", "Either userId or email must be provided");
        }

        Optional<WarehouseMembership> existingOpt = warehouseMembershipRepository
                .findByWarehouseIdAndUserId(warehouseId, targetUser.getId());

        WarehouseMembership membership;
        if (existingOpt.isPresent()) {
            membership = existingOpt.get();
            membership.setRole(request.getRole());
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setAssignedBy(actorId);
            membership.setAssignedAt(Instant.now());
            membership.setRevokedAt(null);
            log.info("Updated membership: User {} in Warehouse {} to role {}", targetUser.getEmail(), warehouse.getCode(), request.getRole());
        } else {
            membership = WarehouseMembership.builder()
                    .warehouse(warehouse)
                    .user(targetUser)
                    .role(request.getRole())
                    .status(MembershipStatus.ACTIVE)
                    .assignedBy(actorId)
                    .assignedAt(Instant.now())
                    .build();
            log.info("Assigned new member: User {} in Warehouse {} with role {}", targetUser.getEmail(), warehouse.getCode(), request.getRole());
        }

        WarehouseMembership saved = warehouseMembershipRepository.save(membership);
        return WarehouseMembershipResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void revokeMember(UUID warehouseId, UUID targetUserId, UUID actorId, String systemRole) {
        warehouseSecurityService.validateWarehouseManager(warehouseId, actorId, systemRole);

        WarehouseMembership membership = warehouseMembershipRepository
                .findByWarehouseIdAndUserId(warehouseId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found for user in warehouse"));

        membership.setStatus(MembershipStatus.REVOKED);
        membership.setRevokedAt(Instant.now());
        warehouseMembershipRepository.save(membership);
        log.info("Revoked membership for user {} in warehouse {}", targetUserId, warehouseId);
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

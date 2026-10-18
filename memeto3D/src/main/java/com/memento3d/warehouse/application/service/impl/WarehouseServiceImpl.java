package com.memento3d.warehouse.application.service.impl;

import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.shared.application.service.AuditLogService;
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
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final UserRepository userRepository;
    private final WarehouseSecurityService warehouseSecurityService;
    private final AuditLogService auditLogService;

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
                .address(request.getAddress() != null ? request.getAddress().trim() : (request.getLocation() != null ? request.getLocation().trim() : null))
                .description(request.getDescription())
                .managerId(request.getManagerId())
                .status(WarehouseStatus.ACTIVE)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Created warehouse ID={}, Code={}", saved.getId(), saved.getCode());

        auditLogService.log(actorId, "CREATE_WAREHOUSE", "Warehouse", saved.getId().toString(), null, "Created code: " + cleanCode);

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
    public WarehouseResponse updateWarehouse(UUID id, UpdateWarehouseRequest request, UUID actorId) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            warehouse.setName(request.getName().trim());
        }
        if (request.getLocation() != null) {
            warehouse.setLocation(request.getLocation().trim());
        }
        if (request.getAddress() != null) {
            warehouse.setAddress(request.getAddress().trim());
        }
        if (request.getDescription() != null) {
            warehouse.setDescription(request.getDescription());
        }
        if (request.getManagerId() != null) {
            warehouse.setManagerId(request.getManagerId());
        }
        warehouse.setUpdatedBy(actorId);

        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Updated warehouse ID={}, Code={}", saved.getId(), saved.getCode());

        auditLogService.log(actorId, "UPDATE_WAREHOUSE", "Warehouse", saved.getId().toString(), null, "Updated code: " + saved.getCode());

        return WarehouseResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouseStatus(UUID id, WarehouseStatus status, UUID actorId) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));

        if (status == WarehouseStatus.INACTIVE && warehouse.getStatus() != WarehouseStatus.INACTIVE) {
            assertWarehouseCanBeDeactivated(id);
        }

        WarehouseStatus oldStatus = warehouse.getStatus();
        warehouse.setStatus(status);
        warehouse.setUpdatedBy(actorId);
        Warehouse saved = warehouseRepository.save(warehouse);
        log.info("Updated warehouse ID={} status from {} to {}", saved.getId(), oldStatus, status);

        auditLogService.log(actorId, "UPDATE_WAREHOUSE_STATUS", "Warehouse", saved.getId().toString(), oldStatus.name(), status.name());

        return WarehouseResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public WarehouseResponse deactivateWarehouse(UUID id) {
        return updateWarehouseStatus(id, WarehouseStatus.INACTIVE, null);
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
        auditLogService.log(actorId, "ASSIGN_WAREHOUSE_MEMBER", "WarehouseMembership", saved.getId().toString(), null, "User: " + targetUser.getEmail() + ", Role: " + request.getRole());

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

        auditLogService.log(actorId, "REVOKE_WAREHOUSE_MEMBER", "WarehouseMembership", membership.getId().toString(), null, "User ID: " + targetUserId);
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

package com.memento3d.warehouse.application.service;

import com.memento3d.shared.exception.BusinessException;
import com.memento3d.warehouse.domain.model.MembershipStatus;
import com.memento3d.warehouse.domain.model.WarehouseMembership;
import com.memento3d.warehouse.domain.model.WarehouseRole;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseSecurityService {

    private final WarehouseMembershipRepository warehouseMembershipRepository;

    /**
     * Checks if current user has access to the specified warehouse.
     * System ADMIN always has full access across all warehouses.
     * Otherwise, user must have an ACTIVE membership in the warehouse.
     */
    public void validateWarehouseAccess(UUID warehouseId, UUID userId, String systemRole) {
        if (warehouseId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WAREHOUSE_REQUIRED", "Warehouse ID is required");
        }

        if (isAdmin(systemRole)) {
            return;
        }

        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        }

        boolean hasActiveMembership = warehouseMembershipRepository
                .existsByWarehouseIdAndUserIdAndStatus(warehouseId, userId, MembershipStatus.ACTIVE);

        if (!hasActiveMembership) {
            log.warn("Access denied: User {} has no active membership in warehouse {}", userId, warehouseId);
            throw new BusinessException(HttpStatus.FORBIDDEN, "WAREHOUSE_ACCESS_DENIED",
                    "Access denied: You do not have active membership in this warehouse");
        }
    }

    /**
     * Checks if current user is allowed to manage members in the warehouse.
     * System ADMIN or Warehouse MANAGER with ACTIVE membership is allowed.
     */
    public void validateWarehouseManager(UUID warehouseId, UUID userId, String systemRole) {
        if (isAdmin(systemRole)) {
            return;
        }

        if (userId == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        }

        Optional<WarehouseMembership> membership = warehouseMembershipRepository
                .findByWarehouseIdAndUserIdAndStatus(warehouseId, userId, MembershipStatus.ACTIVE);

        if (membership.isEmpty() || membership.get().getRole() != WarehouseRole.MANAGER) {
            log.warn("Management denied: User {} is not a MANAGER in warehouse {}", userId, warehouseId);
            throw new BusinessException(HttpStatus.FORBIDDEN, "WAREHOUSE_MANAGER_REQUIRED",
                    "Access denied: Warehouse Manager or System Admin role is required");
        }
    }

    public boolean isAdmin(String systemRole) {
        return "ROLE_ADMIN".equalsIgnoreCase(systemRole) || "ADMIN".equalsIgnoreCase(systemRole);
    }
}

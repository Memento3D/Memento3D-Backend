package com.memento3d.warehouse.application.service;

import com.memento3d.shared.exception.BusinessException;
import com.memento3d.warehouse.domain.model.MembershipStatus;
import com.memento3d.warehouse.domain.model.WarehouseMembership;
import com.memento3d.warehouse.domain.model.WarehouseRole;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseSecurityServiceTest {

    @Mock
    private WarehouseMembershipRepository warehouseMembershipRepository;

    @InjectMocks
    private WarehouseSecurityService warehouseSecurityService;

    private UUID warehouseA;
    private UUID warehouseB;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        warehouseA = UUID.randomUUID();
        warehouseB = UUID.randomUUID();
        employeeId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Test Case 1: Employee belonging to Warehouse A cannot access Warehouse B")
    void employeeOfWarehouseACannotAccessWarehouseB() {
        // Employee has active membership in warehouse A, but NOT in warehouse B
        when(warehouseMembershipRepository.existsByWarehouseIdAndUserIdAndStatus(warehouseB, employeeId, MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> warehouseSecurityService.validateWarehouseAccess(warehouseB, employeeId, "ROLE_OPERATOR"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(be.getErrorCode()).isEqualTo("WAREHOUSE_ACCESS_DENIED");
                });
    }

    @Test
    @DisplayName("Employee with active membership can access their assigned warehouse")
    void employeeWithActiveMembershipCanAccessWarehouse() {
        when(warehouseMembershipRepository.existsByWarehouseIdAndUserIdAndStatus(warehouseA, employeeId, MembershipStatus.ACTIVE))
                .thenReturn(true);

        assertThatCode(() -> warehouseSecurityService.validateWarehouseAccess(warehouseA, employeeId, "ROLE_OPERATOR"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test Case 2: Revoked membership blocks access immediately even if user token is valid")
    void revokedMembershipBlocksAccessImmediately() {
        // In DB, status is REVOKED so existsByWarehouseIdAndUserIdAndStatus(..., ACTIVE) is false
        when(warehouseMembershipRepository.existsByWarehouseIdAndUserIdAndStatus(warehouseA, employeeId, MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> warehouseSecurityService.validateWarehouseAccess(warehouseA, employeeId, "ROLE_OPERATOR"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(be.getErrorCode()).isEqualTo("WAREHOUSE_ACCESS_DENIED");
                });
    }

    @Test
    @DisplayName("System Admin bypasses warehouse membership requirement")
    void adminBypassesWarehouseMembership() {
        assertThatCode(() -> warehouseSecurityService.validateWarehouseAccess(warehouseB, employeeId, "ROLE_ADMIN"))
                .doesNotThrowAnyException();

        verifyNoInteractions(warehouseMembershipRepository);
    }

    @Test
    @DisplayName("Warehouse Manager check: grants access to warehouse manager")
    void warehouseManagerCheck_grantsAccessToManager() {
        WarehouseMembership managerMembership = WarehouseMembership.builder()
                .id(UUID.randomUUID())
                .role(WarehouseRole.MANAGER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(warehouseMembershipRepository.findByWarehouseIdAndUserIdAndStatus(warehouseA, employeeId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(managerMembership));

        assertThatCode(() -> warehouseSecurityService.validateWarehouseManager(warehouseA, employeeId, "ROLE_OPERATOR"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Warehouse Manager check: denies access to regular warehouse staff")
    void warehouseManagerCheck_deniesAccessToRegularStaff() {
        WarehouseMembership staffMembership = WarehouseMembership.builder()
                .id(UUID.randomUUID())
                .role(WarehouseRole.WAREHOUSE_STAFF)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(warehouseMembershipRepository.findByWarehouseIdAndUserIdAndStatus(warehouseA, employeeId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(staffMembership));

        assertThatThrownBy(() -> warehouseSecurityService.validateWarehouseManager(warehouseA, employeeId, "ROLE_OPERATOR"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(be.getErrorCode()).isEqualTo("WAREHOUSE_MANAGER_REQUIRED");
                });
    }
}

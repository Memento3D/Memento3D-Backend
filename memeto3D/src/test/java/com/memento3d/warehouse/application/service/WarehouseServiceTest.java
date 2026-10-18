package com.memento3d.warehouse.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.warehouse.api.request.AssignMemberRequest;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseMembershipResponse;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.application.service.impl.WarehouseServiceImpl;
import com.memento3d.warehouse.domain.model.MembershipStatus;
import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.domain.model.WarehouseMembership;
import com.memento3d.warehouse.domain.model.WarehouseRole;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseMembershipRepository;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseMembershipRepository warehouseMembershipRepository;

    @Mock
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseSecurityService warehouseSecurityService;

    @InjectMocks
    private WarehouseServiceImpl warehouseService;

    private UUID warehouseId;
    private Warehouse mockWarehouse;
    private User mockUser;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        mockWarehouse = Warehouse.builder()
                .id(warehouseId)
                .code("WH-HCM-01")
                .name("Central Hub Thu Duc")
                .location("High-Tech Park")
                .status(WarehouseStatus.ACTIVE)
                .build();

        mockUser = User.builder()
                .id(UUID.randomUUID())
                .email("operator@memento3d.vn")
                .fullName("Warehouse Staff")
                .role(Role.ROLE_OPERATOR)
                .build();
    }

    @Test
    @DisplayName("createWarehouse: succeeds when code is unique")
    void createWarehouse_success() {
        CreateWarehouseRequest request = CreateWarehouseRequest.builder()
                .code("WH-HCM-01")
                .name("Central Hub Thu Duc")
                .location("High-Tech Park")
                .build();

        when(warehouseRepository.existsByCodeIgnoreCase("WH-HCM-01")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(mockWarehouse);

        WarehouseResponse response = warehouseService.createWarehouse(request, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("WH-HCM-01");
        verify(warehouseRepository).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("createWarehouse: throws 409 Conflict when code is duplicate")
    void createWarehouse_duplicateCode() {
        CreateWarehouseRequest request = CreateWarehouseRequest.builder()
                .code("WH-HCM-01")
                .name("Central Hub Thu Duc")
                .build();

        when(warehouseRepository.existsByCodeIgnoreCase("WH-HCM-01")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.createWarehouse(request, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("DUPLICATE_WAREHOUSE_CODE");
                });

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("deactivateWarehouse: succeeds when no active stock or reservations exist")
    void deactivateWarehouse_success() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(inventoryBalanceRepository.existsByWarehouseIdAndOnHandGramGreaterThan(warehouseId, BigDecimal.ZERO)).thenReturn(false);
        when(inventoryBalanceRepository.existsByWarehouseIdAndReservedGramGreaterThan(warehouseId, BigDecimal.ZERO)).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

        WarehouseResponse response = warehouseService.deactivateWarehouse(warehouseId);

        assertThat(response.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
        assertThat(mockWarehouse.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
    }

    @Test
    @DisplayName("deactivateWarehouse: fails when active stock is present (F02 business rule)")
    void deactivateWarehouse_failsWhenStockPresent() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(inventoryBalanceRepository.existsByWarehouseIdAndOnHandGramGreaterThan(warehouseId, BigDecimal.ZERO)).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.deactivateWarehouse(warehouseId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("WAREHOUSE_HAS_STOCK");
                });

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("deactivateWarehouse: fails when active reservations exist (F02 business rule)")
    void deactivateWarehouse_failsWhenReservationPresent() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(inventoryBalanceRepository.existsByWarehouseIdAndOnHandGramGreaterThan(warehouseId, BigDecimal.ZERO)).thenReturn(false);
        when(inventoryBalanceRepository.existsByWarehouseIdAndReservedGramGreaterThan(warehouseId, BigDecimal.ZERO)).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.deactivateWarehouse(warehouseId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("WAREHOUSE_HAS_RESERVATIONS");
                });

        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    @DisplayName("getMyWarehouses: returns active memberships for operator")
    void getMyWarehouses_operatorReturnsScopedWarehouses() {
        UUID userId = mockUser.getId();
        WarehouseMembership membership = WarehouseMembership.builder()
                .id(UUID.randomUUID())
                .warehouse(mockWarehouse)
                .user(mockUser)
                .role(WarehouseRole.WAREHOUSE_STAFF)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(warehouseSecurityService.isAdmin("ROLE_OPERATOR")).thenReturn(false);
        when(warehouseMembershipRepository.findAllByUserIdAndStatusWithWarehouse(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership));

        List<WarehouseResponse> responses = warehouseService.getMyWarehouses(userId, "ROLE_OPERATOR");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCode()).isEqualTo("WH-HCM-01");
    }

    @Test
    @DisplayName("assignMember: assigns new staff member to warehouse")
    void assignMember_success() {
        UUID actorId = UUID.randomUUID();
        AssignMemberRequest request = AssignMemberRequest.builder()
                .email("operator@memento3d.vn")
                .role(WarehouseRole.WAREHOUSE_STAFF)
                .build();

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(userRepository.findByEmail("operator@memento3d.vn")).thenReturn(Optional.of(mockUser));
        when(warehouseMembershipRepository.findByWarehouseIdAndUserId(warehouseId, mockUser.getId()))
                .thenReturn(Optional.empty());
        when(warehouseMembershipRepository.save(any(WarehouseMembership.class)))
                .thenAnswer(inv -> {
                    WarehouseMembership m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    return m;
                });

        WarehouseMembershipResponse response = warehouseService.assignMember(warehouseId, request, actorId, "ROLE_ADMIN");

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(WarehouseRole.WAREHOUSE_STAFF);
        assertThat(response.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(warehouseMembershipRepository).save(any(WarehouseMembership.class));
    }

    @Test
    @DisplayName("revokeMember: marks membership as REVOKED")
    void revokeMember_success() {
        UUID actorId = UUID.randomUUID();
        WarehouseMembership membership = WarehouseMembership.builder()
                .id(UUID.randomUUID())
                .warehouse(mockWarehouse)
                .user(mockUser)
                .role(WarehouseRole.WAREHOUSE_STAFF)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(warehouseMembershipRepository.findByWarehouseIdAndUserId(warehouseId, mockUser.getId()))
                .thenReturn(Optional.of(membership));

        warehouseService.revokeMember(warehouseId, mockUser.getId(), actorId, "ROLE_ADMIN");

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.REVOKED);
        assertThat(membership.getRevokedAt()).isNotNull();
        verify(warehouseMembershipRepository).save(membership);
    }
}

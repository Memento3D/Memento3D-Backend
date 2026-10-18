package com.memento3d.warehouse.application.service;

import com.memento3d.warehouse.api.request.AssignMemberRequest;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.request.UpdateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseMembershipResponse;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WarehouseService {

    WarehouseResponse createWarehouse(CreateWarehouseRequest request, UUID actorId);

    WarehouseResponse getWarehouse(UUID id);

    Page<WarehouseResponse> listWarehouses(WarehouseStatus status, String search, Pageable pageable);

    WarehouseResponse updateWarehouse(UUID id, UpdateWarehouseRequest request, UUID actorId);

    WarehouseResponse updateWarehouseStatus(UUID id, WarehouseStatus status, UUID actorId);

    WarehouseResponse deactivateWarehouse(UUID id);

    // F01: Membership & Warehouse Scoping
    List<WarehouseResponse> getMyWarehouses(UUID userId, String systemRole);

    List<WarehouseMembershipResponse> getWarehouseMembers(UUID warehouseId, UUID actorId, String systemRole);

    WarehouseMembershipResponse assignMember(UUID warehouseId, AssignMemberRequest request, UUID actorId, String systemRole);

    void revokeMember(UUID warehouseId, UUID targetUserId, UUID actorId, String systemRole);
}

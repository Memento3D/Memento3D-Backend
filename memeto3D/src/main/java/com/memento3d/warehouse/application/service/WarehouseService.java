package com.memento3d.warehouse.application.service;

import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.request.UpdateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WarehouseService {

    WarehouseResponse createWarehouse(CreateWarehouseRequest request, UUID actorId);

    WarehouseResponse getWarehouse(UUID id);

    Page<WarehouseResponse> listWarehouses(WarehouseStatus status, String search, Pageable pageable);

    WarehouseResponse updateWarehouse(UUID id, UpdateWarehouseRequest request);

    WarehouseResponse deactivateWarehouse(UUID id);
}

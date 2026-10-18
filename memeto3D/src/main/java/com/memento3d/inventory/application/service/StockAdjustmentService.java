package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateStockAdjustmentRequest;
import com.memento3d.inventory.api.response.StockAdjustmentResponse;
import com.memento3d.inventory.domain.model.StockAdjustmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StockAdjustmentService {
    StockAdjustmentResponse createAdjustment(CreateStockAdjustmentRequest request, UUID actorId);
    StockAdjustmentResponse getAdjustment(UUID id);
    Page<StockAdjustmentResponse> listAdjustments(UUID warehouseId, UUID materialId, StockAdjustmentStatus status, Pageable pageable);
    StockAdjustmentResponse submitAdjustment(UUID id, UUID actorId);
    StockAdjustmentResponse approveAdjustment(UUID id, UUID actorId);
    StockAdjustmentResponse rejectAdjustment(UUID id, String reason, UUID actorId);
    StockAdjustmentResponse applyAdjustment(UUID id, UUID actorId);
}

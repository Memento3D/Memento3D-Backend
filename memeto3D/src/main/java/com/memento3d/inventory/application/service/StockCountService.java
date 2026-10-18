package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateStockCountRequest;
import com.memento3d.inventory.api.request.UpdateStockCountItemsRequest;
import com.memento3d.inventory.api.response.StockCountResponse;
import com.memento3d.inventory.domain.model.StockCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StockCountService {
    StockCountResponse createStockCount(CreateStockCountRequest request, UUID actorId);
    StockCountResponse getStockCount(UUID id);
    Page<StockCountResponse> listStockCounts(UUID warehouseId, StockCountStatus status, Pageable pageable);
    StockCountResponse startCount(UUID id, UUID actorId);
    StockCountResponse recordCounts(UUID id, UpdateStockCountItemsRequest request, UUID actorId);
    StockCountResponse completeCount(UUID id, UUID actorId);
}

package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateWarehouseTransferRequest;
import com.memento3d.inventory.api.response.WarehouseTransferResponse;
import com.memento3d.inventory.domain.model.WarehouseTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WarehouseTransferService {
    WarehouseTransferResponse createTransfer(CreateWarehouseTransferRequest request, UUID actorId);
    WarehouseTransferResponse getTransfer(UUID id);
    Page<WarehouseTransferResponse> listTransfers(UUID warehouseId, WarehouseTransferStatus status, Pageable pageable);
    WarehouseTransferResponse submitTransfer(UUID id, UUID actorId);
    WarehouseTransferResponse approveTransfer(UUID id, UUID actorId);
    WarehouseTransferResponse shipTransfer(UUID id, UUID actorId);
    WarehouseTransferResponse receiveTransfer(UUID id, UUID actorId);
    WarehouseTransferResponse cancelTransfer(UUID id, UUID actorId);
}

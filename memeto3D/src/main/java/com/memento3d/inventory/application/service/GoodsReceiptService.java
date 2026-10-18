package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateGoodsReceiptRequest;
import com.memento3d.inventory.api.request.UpdateGoodsReceiptRequest;
import com.memento3d.inventory.api.response.GoodsReceiptResponse;
import com.memento3d.inventory.domain.model.GoodsReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GoodsReceiptService {
    GoodsReceiptResponse createReceipt(CreateGoodsReceiptRequest request, UUID actorId);
    GoodsReceiptResponse getReceipt(UUID id);
    Page<GoodsReceiptResponse> listReceipts(UUID warehouseId, GoodsReceiptStatus status, Pageable pageable);
    GoodsReceiptResponse updateReceipt(UUID id, UpdateGoodsReceiptRequest request, UUID actorId);
    GoodsReceiptResponse submitReceipt(UUID id, UUID actorId);
    GoodsReceiptResponse approveReceipt(UUID id, UUID actorId);
    GoodsReceiptResponse completeReceipt(UUID id, UUID actorId);
    GoodsReceiptResponse cancelReceipt(UUID id, UUID actorId);
}

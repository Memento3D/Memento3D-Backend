package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateStockIssueRequest;
import com.memento3d.inventory.api.request.CreateStockReceiptRequest;
import com.memento3d.inventory.api.response.InventoryBalanceResponse;
import com.memento3d.inventory.api.response.InventoryTransactionResponse;
import com.memento3d.inventory.api.response.StockIssueResponse;
import com.memento3d.inventory.api.response.StockReceiptResponse;
import com.memento3d.inventory.domain.model.InventoryTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryService {

    Page<InventoryBalanceResponse> listBalances(UUID warehouseId, UUID materialId, Pageable pageable);

    StockReceiptResponse createReceipt(CreateStockReceiptRequest request, UUID actorId);

    Page<StockReceiptResponse> listReceipts(UUID warehouseId, UUID materialId, Pageable pageable);

    StockIssueResponse createIssue(CreateStockIssueRequest request, UUID actorId);

    Page<StockIssueResponse> listIssues(UUID warehouseId, UUID materialId, Pageable pageable);

    Page<InventoryTransactionResponse> listTransactions(
            UUID warehouseId,
            UUID materialId,
            InventoryTransactionType type,
            UUID printJobId,
            Pageable pageable
    );
}

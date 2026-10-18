package com.memento3d.inventory.application.service;

import com.memento3d.inventory.api.request.CreateEnterpriseStockIssueRequest;
import com.memento3d.inventory.api.response.EnterpriseStockIssueResponse;
import com.memento3d.inventory.domain.model.EnterpriseStockIssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EnterpriseStockIssueService {
    EnterpriseStockIssueResponse createIssue(CreateEnterpriseStockIssueRequest request, UUID actorId);
    EnterpriseStockIssueResponse getIssue(UUID id);
    Page<EnterpriseStockIssueResponse> listIssues(UUID warehouseId, EnterpriseStockIssueStatus status, Pageable pageable);
    EnterpriseStockIssueResponse submitIssue(UUID id, UUID actorId);
    EnterpriseStockIssueResponse approveIssue(UUID id, UUID actorId);
    EnterpriseStockIssueResponse completeIssue(UUID id, UUID actorId);
    EnterpriseStockIssueResponse cancelIssue(UUID id, UUID actorId);
}

package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.EnterpriseStockIssueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnterpriseStockIssueItemRepository extends JpaRepository<EnterpriseStockIssueItem, UUID> {
    List<EnterpriseStockIssueItem> findByIssueId(UUID issueId);
    boolean existsByMaterialId(UUID materialId);
}

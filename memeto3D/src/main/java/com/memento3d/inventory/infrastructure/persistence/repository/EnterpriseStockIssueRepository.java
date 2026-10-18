package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.EnterpriseStockIssue;
import com.memento3d.inventory.domain.model.EnterpriseStockIssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnterpriseStockIssueRepository extends JpaRepository<EnterpriseStockIssue, UUID> {

    @EntityGraph(attributePaths = {"warehouse", "items", "items.material"})
    Optional<EnterpriseStockIssue> findWithItemsById(UUID id);

    Optional<EnterpriseStockIssue> findByIssueCode(String issueCode);

    boolean existsByIssueCode(String issueCode);

    @Query("SELECT i FROM EnterpriseStockIssue i WHERE " +
            "(:warehouseId IS NULL OR i.warehouse.id = :warehouseId) AND " +
            "(:status IS NULL OR i.status = :status)")
    Page<EnterpriseStockIssue> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("status") EnterpriseStockIssueStatus status,
            Pageable pageable
    );

    long countByStatus(EnterpriseStockIssueStatus status);

    long countByWarehouseIdAndStatus(UUID warehouseId, EnterpriseStockIssueStatus status);
}

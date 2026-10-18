package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.StockIssue;
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
public interface StockIssueRepository extends JpaRepository<StockIssue, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<StockIssue> findByIdempotencyKey(String idempotencyKey);

    Optional<StockIssue> findByIssueNumber(String issueNumber);

    @EntityGraph(attributePaths = {"warehouse", "material"})
    @Query("SELECT i FROM StockIssue i WHERE " +
            "(:warehouseId IS NULL OR i.warehouse.id = :warehouseId) AND " +
            "(:materialId IS NULL OR i.material.id = :materialId)")
    Page<StockIssue> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("materialId") UUID materialId,
            Pageable pageable
    );
}

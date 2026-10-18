package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.StockAdjustment;
import com.memento3d.inventory.domain.model.StockAdjustmentStatus;
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
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

    @EntityGraph(attributePaths = {"warehouse", "material"})
    Optional<StockAdjustment> findWithDetailsById(UUID id);

    Optional<StockAdjustment> findByAdjustmentCode(String adjustmentCode);

    boolean existsByAdjustmentCode(String adjustmentCode);

    @Query("SELECT a FROM StockAdjustment a WHERE " +
            "(:warehouseId IS NULL OR a.warehouse.id = :warehouseId) AND " +
            "(:materialId IS NULL OR a.material.id = :materialId) AND " +
            "(:status IS NULL OR a.status = :status)")
    Page<StockAdjustment> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("materialId") UUID materialId,
            @Param("status") StockAdjustmentStatus status,
            Pageable pageable
    );

    long countByStatus(StockAdjustmentStatus status);
}

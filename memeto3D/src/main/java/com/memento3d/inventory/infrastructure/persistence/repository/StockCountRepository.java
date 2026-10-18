package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.StockCount;
import com.memento3d.inventory.domain.model.StockCountStatus;
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
public interface StockCountRepository extends JpaRepository<StockCount, UUID> {

    @EntityGraph(attributePaths = {"warehouse", "items", "items.material"})
    Optional<StockCount> findWithItemsById(UUID id);

    Optional<StockCount> findByCountCode(String countCode);

    boolean existsByCountCode(String countCode);

    @Query("SELECT c FROM StockCount c WHERE " +
            "(:warehouseId IS NULL OR c.warehouse.id = :warehouseId) AND " +
            "(:status IS NULL OR c.status = :status)")
    Page<StockCount> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("status") StockCountStatus status,
            Pageable pageable
    );

    long countByStatus(StockCountStatus status);
}

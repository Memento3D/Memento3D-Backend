package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.WarehouseTransfer;
import com.memento3d.inventory.domain.model.WarehouseTransferStatus;
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
public interface WarehouseTransferRepository extends JpaRepository<WarehouseTransfer, UUID> {

    @EntityGraph(attributePaths = {"sourceWarehouse", "destinationWarehouse", "items", "items.material"})
    Optional<WarehouseTransfer> findWithItemsById(UUID id);

    Optional<WarehouseTransfer> findByTransferCode(String transferCode);

    boolean existsByTransferCode(String transferCode);

    @EntityGraph(attributePaths = {"sourceWarehouse", "destinationWarehouse"})
    @Query(
        value = """
            SELECT t FROM WarehouseTransfer t WHERE
            (:warehouseId IS NULL OR t.sourceWarehouse.id = :warehouseId OR t.destinationWarehouse.id = :warehouseId) AND
            (:status IS NULL OR t.status = :status)
            """,
        countQuery = """
            SELECT COUNT(t) FROM WarehouseTransfer t WHERE
            (:warehouseId IS NULL OR t.sourceWarehouse.id = :warehouseId OR t.destinationWarehouse.id = :warehouseId) AND
            (:status IS NULL OR t.status = :status)
            """
    )
    Page<WarehouseTransfer> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("status") WarehouseTransferStatus status,
            Pageable pageable
    );

    long countByStatus(WarehouseTransferStatus status);
}

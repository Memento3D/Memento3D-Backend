package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.GoodsReceipt;
import com.memento3d.inventory.domain.model.GoodsReceiptStatus;
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
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

    @EntityGraph(attributePaths = {"warehouse", "items", "items.material"})
    Optional<GoodsReceipt> findWithItemsById(UUID id);

    Optional<GoodsReceipt> findByReceiptCode(String receiptCode);

    boolean existsByReceiptCode(String receiptCode);

    @EntityGraph(attributePaths = {"warehouse"})
    @Query(
        value = """
            SELECT r FROM GoodsReceipt r WHERE
            (:warehouseId IS NULL OR r.warehouse.id = :warehouseId) AND
            (:status IS NULL OR r.status = :status)
            """,
        countQuery = """
            SELECT COUNT(r) FROM GoodsReceipt r WHERE
            (:warehouseId IS NULL OR r.warehouse.id = :warehouseId) AND
            (:status IS NULL OR r.status = :status)
            """
    )
    Page<GoodsReceipt> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("status") GoodsReceiptStatus status,
            Pageable pageable
    );

    long countByStatus(GoodsReceiptStatus status);

    long countByWarehouseIdAndStatus(UUID warehouseId, GoodsReceiptStatus status);
}

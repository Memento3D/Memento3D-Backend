package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.StockReceipt;
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
public interface StockReceiptRepository extends JpaRepository<StockReceipt, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<StockReceipt> findByIdempotencyKey(String idempotencyKey);

    Optional<StockReceipt> findByReceiptNumber(String receiptNumber);

    @EntityGraph(attributePaths = {"warehouse", "material"})
    @Query("SELECT r FROM StockReceipt r WHERE " +
            "(:warehouseId IS NULL OR r.warehouse.id = :warehouseId) AND " +
            "(:materialId IS NULL OR r.material.id = :materialId)")
    Page<StockReceipt> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("materialId") UUID materialId,
            Pageable pageable
    );
}

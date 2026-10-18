package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.InventoryTransaction;
import com.memento3d.inventory.domain.model.InventoryTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    @EntityGraph(attributePaths = {"warehouse", "material"})
    @Query("SELECT t FROM InventoryTransaction t WHERE " +
            "(:warehouseId IS NULL OR t.warehouse.id = :warehouseId) AND " +
            "(:materialId IS NULL OR t.material.id = :materialId) AND " +
            "(:transactionType IS NULL OR t.transactionType = :transactionType) AND " +
            "(:printJobId IS NULL OR t.printJobId = :printJobId) " +
            "ORDER BY t.createdAt DESC")
    Page<InventoryTransaction> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("materialId") UUID materialId,
            @Param("transactionType") InventoryTransactionType transactionType,
            @Param("printJobId") UUID printJobId,
            Pageable pageable
    );
}

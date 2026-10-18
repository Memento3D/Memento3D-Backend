package com.memento3d.inventory.infrastructure.persistence.repository;

import com.memento3d.inventory.domain.model.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {

    @EntityGraph(attributePaths = {"warehouse", "material"})
    Optional<InventoryBalance> findByWarehouseIdAndMaterialId(UUID warehouseId, UUID materialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b WHERE b.warehouse.id = :warehouseId AND b.material.id = :materialId")
    Optional<InventoryBalance> findByWarehouseIdAndMaterialIdForUpdate(
            @Param("warehouseId") UUID warehouseId,
            @Param("materialId") UUID materialId
    );

    boolean existsByWarehouseIdAndOnHandGramGreaterThan(UUID warehouseId, BigDecimal amount);

    boolean existsByWarehouseIdAndReservedGramGreaterThan(UUID warehouseId, BigDecimal amount);

    @EntityGraph(attributePaths = {"warehouse", "material"})
    List<InventoryBalance> findByWarehouseId(UUID warehouseId);

    @EntityGraph(attributePaths = {"warehouse", "material"})
    @Query(
        value = """
            SELECT b FROM InventoryBalance b WHERE
            (:warehouseId IS NULL OR b.warehouse.id = :warehouseId) AND
            (:materialId IS NULL OR b.material.id = :materialId)
            """,
        countQuery = """
            SELECT COUNT(b) FROM InventoryBalance b WHERE
            (:warehouseId IS NULL OR b.warehouse.id = :warehouseId) AND
            (:materialId IS NULL OR b.material.id = :materialId)
            """
    )
    Page<InventoryBalance> findAllWithFilters(
            @Param("warehouseId") UUID warehouseId,
            @Param("materialId") UUID materialId,
            Pageable pageable
    );

    @Query("SELECT COUNT(b) FROM InventoryBalance b WHERE " +
            "(:warehouseId IS NULL OR b.warehouse.id = :warehouseId) AND " +
            "(b.onHandGram - b.reservedGram) <= b.minimumStock AND (b.onHandGram - b.reservedGram) > 0")
    long countLowStock(@Param("warehouseId") UUID warehouseId);

    @Query("SELECT COUNT(b) FROM InventoryBalance b WHERE " +
            "(:warehouseId IS NULL OR b.warehouse.id = :warehouseId) AND " +
            "(b.onHandGram - b.reservedGram) <= 0")
    long countOutOfStock(@Param("warehouseId") UUID warehouseId);

    @Query("SELECT COALESCE(SUM(b.onHandGram * b.material.unitCost), 0) FROM InventoryBalance b WHERE " +
            "(:warehouseId IS NULL OR b.warehouse.id = :warehouseId)")
    BigDecimal calculateTotalInventoryValue(@Param("warehouseId") UUID warehouseId);

    @EntityGraph(attributePaths = {"warehouse", "material"})
    @Query("SELECT b FROM InventoryBalance b WHERE " +
            "(:warehouseId IS NULL OR b.warehouse.id = :warehouseId) AND " +
            "(b.onHandGram - b.reservedGram) <= b.minimumStock")
    List<InventoryBalance> findLowStockItems(@Param("warehouseId") UUID warehouseId);
}

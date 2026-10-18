package com.memento3d.warehouse.infrastructure.persistence.repository;

import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Optional<Warehouse> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    List<Warehouse> findByManagerId(UUID managerId);

    List<Warehouse> findByStatus(WarehouseStatus status);

    long countByStatus(WarehouseStatus status);

    @Query("SELECT w FROM Warehouse w WHERE " +
            "(:status IS NULL OR w.status = :status) AND " +
            "(:search IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(w.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Warehouse> findAllWithFilters(
            @Param("status") WarehouseStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}

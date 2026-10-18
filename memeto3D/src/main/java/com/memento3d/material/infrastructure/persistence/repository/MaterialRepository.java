package com.memento3d.material.infrastructure.persistence.repository;

import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.domain.model.MaterialType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    Optional<Material> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

    @Query("SELECT m FROM Material m WHERE " +
            "(:status IS NULL OR m.status = :status) AND " +
            "(:materialType IS NULL OR m.materialType = :materialType) AND " +
            "(:search IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.sku) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Material> findAllWithFilters(
            @Param("status") MaterialStatus status,
            @Param("materialType") MaterialType materialType,
            @Param("search") String search,
            Pageable pageable
    );
}

package com.memento3d.warehouse.infrastructure.persistence.repository;

import com.memento3d.warehouse.domain.model.MembershipStatus;
import com.memento3d.warehouse.domain.model.WarehouseMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseMembershipRepository extends JpaRepository<WarehouseMembership, UUID> {

    Optional<WarehouseMembership> findByWarehouseIdAndUserId(UUID warehouseId, UUID userId);

    Optional<WarehouseMembership> findByWarehouseIdAndUserIdAndStatus(UUID warehouseId, UUID userId, MembershipStatus status);

    boolean existsByWarehouseIdAndUserIdAndStatus(UUID warehouseId, UUID userId, MembershipStatus status);

    @Query("SELECT wm FROM WarehouseMembership wm JOIN FETCH wm.warehouse WHERE wm.user.id = :userId AND wm.status = :status")
    List<WarehouseMembership> findAllByUserIdAndStatusWithWarehouse(@Param("userId") UUID userId, @Param("status") MembershipStatus status);

    @Query("SELECT wm FROM WarehouseMembership wm JOIN FETCH wm.user WHERE wm.warehouse.id = :warehouseId ORDER BY wm.assignedAt DESC")
    List<WarehouseMembership> findAllByWarehouseIdWithUser(@Param("warehouseId") UUID warehouseId);

    long countByWarehouseIdAndStatus(UUID warehouseId, MembershipStatus status);
}

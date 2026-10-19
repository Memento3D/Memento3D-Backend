package com.memento3d.entitlement.infrastructure.persistence.repository;

import com.memento3d.entitlement.domain.model.Entitlement;
import com.memento3d.entitlement.domain.model.EntitlementStatus;
import com.memento3d.entitlement.domain.model.EntitlementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

    List<Entitlement> findByOwnerIdAndStatus(UUID ownerId, EntitlementStatus status);

    Optional<Entitlement> findByOwnerIdAndResourceIdAndEntitlementTypeAndStatus(
            UUID ownerId,
            UUID resourceId,
            EntitlementType entitlementType,
            EntitlementStatus status
    );

    boolean existsByOwnerIdAndResourceIdAndEntitlementTypeAndStatus(
            UUID ownerId,
            UUID resourceId,
            EntitlementType entitlementType,
            EntitlementStatus status
    );

    List<Entitlement> findByOrderItemId(UUID orderItemId);
}

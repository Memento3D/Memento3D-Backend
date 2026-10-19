package com.memento3d.quality.infrastructure.persistence.repository;

import com.memento3d.quality.domain.model.PhysicalQualityCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhysicalQualityCheckRepository extends JpaRepository<PhysicalQualityCheck, UUID> {
    List<PhysicalQualityCheck> findByPrintJobIdOrderByCreatedAtDesc(UUID printJobId);
    Optional<PhysicalQualityCheck> findFirstByPrintJobIdOrderByCreatedAtDesc(UUID printJobId);
    List<PhysicalQualityCheck> findByOrderItemIdOrderByCreatedAtDesc(UUID orderItemId);
}

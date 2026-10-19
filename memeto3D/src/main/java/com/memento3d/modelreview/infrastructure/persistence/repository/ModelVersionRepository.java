package com.memento3d.modelreview.infrastructure.persistence.repository;

import com.memento3d.modelreview.domain.model.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {
    List<ModelVersion> findByScan_IdOrderByVersionNumberDesc(UUID scanId);
    Optional<ModelVersion> findFirstByScan_IdOrderByVersionNumberDesc(UUID scanId);
    Optional<ModelVersion> findByScan_IdAndVersionNumber(UUID scanId, int versionNumber);
}

package com.memento3d.scans.infrastructure.persistence.repository;

import com.memento3d.scans.domain.model.ScanVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanVideoRepository extends JpaRepository<ScanVideo, UUID> {
    Optional<ScanVideo> findByIdAndDeletedAtIsNull(UUID id);
    Optional<ScanVideo> findFirstByScanIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID scanId);
}

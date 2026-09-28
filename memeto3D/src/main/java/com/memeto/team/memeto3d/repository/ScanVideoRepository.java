package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.ScanVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanVideoRepository extends JpaRepository<ScanVideo, UUID> {
    Optional<ScanVideo> findByIdAndDeletedAtIsNull(UUID id);
    Optional<ScanVideo> findFirstByScanIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID scanId);
}

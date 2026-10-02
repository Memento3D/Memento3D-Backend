package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.QualityCheckRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualityCheckRunRepository extends JpaRepository<QualityCheckRun, UUID> {
    List<QualityCheckRun> findByScanIdOrderByRunNumberDesc(UUID scanId);
    Optional<QualityCheckRun> findByIdAndScanId(UUID id, UUID scanId);
}

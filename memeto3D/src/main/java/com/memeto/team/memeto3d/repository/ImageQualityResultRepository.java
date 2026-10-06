package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.ImageQualityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageQualityResultRepository extends JpaRepository<ImageQualityResult, UUID> {

    Optional<ImageQualityResult> findByQualityCheckRunIdAndScanImageId(UUID qualityCheckRunId, UUID scanImageId);

    List<ImageQualityResult> findByQualityCheckRunId(UUID qualityCheckRunId);

    List<ImageQualityResult> findByQualityCheckRunIdAndDuplicateGroupId(UUID qualityCheckRunId, UUID duplicateGroupId);
}

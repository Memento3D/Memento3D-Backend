package com.memento3d.quality.infrastructure.persistence.repository;

import com.memento3d.quality.domain.model.QualityCheckRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QualityCheckRunRepository extends JpaRepository<QualityCheckRun, UUID> {
    List<QualityCheckRun> findByScanIdOrderByRunNumberDesc(UUID scanId);
}

package com.memento3d.models.infrastructure.persistence.repository;

import com.memento3d.models.domain.model.ModelOutput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelOutputRepository
        extends JpaRepository<ModelOutput, UUID> {

    Optional<ModelOutput> findByJob_IdAndAttemptIdAndFormat(
            UUID jobId,
            UUID attemptId,
            String format
    );

    List<ModelOutput> findByJob_Scan_IdOrderByCreatedAtDesc(
            UUID scanId
    );
}
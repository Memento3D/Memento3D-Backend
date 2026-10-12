package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.ModelOutput;
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
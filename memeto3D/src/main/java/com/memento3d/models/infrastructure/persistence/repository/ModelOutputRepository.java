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

    List<ModelOutput> findByJob_IdOrderByCreatedAtDesc(
            UUID jobId
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"job","job.scan","job.scan.user"})
    @org.springframework.data.jpa.repository.Query("select o from ModelOutput o where o.id=:id")
    Optional<ModelOutput> findSceneOutput(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from ModelOutput o where o.id=:id")
    Optional<ModelOutput> findSceneOutputForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
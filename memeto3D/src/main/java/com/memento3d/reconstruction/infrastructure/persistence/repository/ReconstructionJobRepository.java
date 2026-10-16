package com.memento3d.reconstruction.infrastructure.persistence.repository;

import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.memento3d.reconstruction.domain.model.ExecutionMode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconstructionJobRepository
        extends JpaRepository<ReconstructionJob, UUID> {

    List<ReconstructionJob> findByScan_IdOrderByCreatedAtDesc(
            UUID scanId
    );

    Optional<ReconstructionJob>
    findFirstByScan_IdOrderByCreatedAtDescIdDesc(UUID scanId);

    boolean existsByScan_IdAndStatusIn(
            UUID scanId,
            Collection<ReconstructionJobStatus> statuses
    );

    List<ReconstructionJob> findByStatusOrderByQueuedAtAsc(
            ReconstructionJobStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT j
        FROM ReconstructionJob j
        WHERE j.id = :jobId
        """)
    Optional<ReconstructionJob> findByIdForUpdate(
            @Param("jobId") UUID jobId
    );

    List<ReconstructionJob>
    findByExecutionModeAndStatusOrderByQueuedAtAsc(
            ExecutionMode executionMode,
            ReconstructionJobStatus status,
            Pageable pageable
    );

    org.springframework.data.domain.Page<ReconstructionJob>
    findByScan_User_IdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    org.springframework.data.domain.Page<ReconstructionJob>
    findAllByOrderByCreatedAtDesc(
            Pageable pageable
    );

}
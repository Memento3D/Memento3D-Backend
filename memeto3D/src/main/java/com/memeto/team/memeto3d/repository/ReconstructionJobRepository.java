package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.ReconstructionJob;
import com.memeto.team.memeto3d.entity.ReconstructionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.memeto.team.memeto3d.entity.ExecutionMode;

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

}
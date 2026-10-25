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

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"scan"})
    org.springframework.data.domain.Page<ReconstructionJob>
    findByScan_User_IdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"scan"})
    org.springframework.data.domain.Page<ReconstructionJob>
    findAllByOrderByCreatedAtDesc(
            Pageable pageable
    );

    long countByStatus(ReconstructionJobStatus status);
    long countByTier(com.memento3d.reconstruction.domain.model.ReconstructionTier tier);
    long countByScan_User_Id(UUID userId);

    @Query("SELECT j.status, COUNT(j) FROM ReconstructionJob j GROUP BY j.status")
    List<Object[]> countJobsGroupedByStatus();

    @Query("SELECT j.tier, COUNT(j) FROM ReconstructionJob j GROUP BY j.tier")
    List<Object[]> countJobsGroupedByTier();

    @Query("SELECT j.tier, j.status, COUNT(j) FROM ReconstructionJob j GROUP BY j.tier, j.status")
    List<Object[]> countGroupedByTierAndStatus();

    @Query("SELECT DISTINCT s.user.id FROM ReconstructionJob j JOIN j.scan s WHERE j.tier = :tier")
    java.util.Set<UUID> findDistinctUserIdsByTier(@Param("tier") com.memento3d.reconstruction.domain.model.ReconstructionTier tier);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"scan"})
    @Query(
        value = """
            SELECT j FROM ReconstructionJob j WHERE
            (:status IS NULL OR j.status = :status) AND
            (:tier IS NULL OR j.tier = :tier)
            """,
        countQuery = """
            SELECT COUNT(j) FROM ReconstructionJob j WHERE
            (:status IS NULL OR j.status = :status) AND
            (:tier IS NULL OR j.tier = :tier)
            """
    )
    org.springframework.data.domain.Page<ReconstructionJob> findAllWithFilters(
            @Param("status") ReconstructionJobStatus status,
            @Param("tier") com.memento3d.reconstruction.domain.model.ReconstructionTier tier,
            Pageable pageable
    );

}
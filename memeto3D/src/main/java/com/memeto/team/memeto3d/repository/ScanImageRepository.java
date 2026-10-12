package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.ScanImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanImageRepository extends JpaRepository<ScanImage, UUID> {

    /**
     * Returns all non-deleted images for a scan sorted by sort_order ASC, created_at ASC.
     * Includes images with any upload_status (uploaded, pending, failed) — UC-03.
     */
    @EntityGraph(attributePaths = {"scan"})
    @Query("SELECT si FROM ScanImage si WHERE si.scan.id = :scanId AND si.deletedAt IS NULL " +
           "ORDER BY si.sortOrder ASC, si.createdAt ASC")
    Page<ScanImage> findByScanIdAndDeletedAtIsNull(@Param("scanId") UUID scanId, Pageable pageable);

    /** Counts the current max sort_order so new images get appended. */
    @Query("SELECT COALESCE(MAX(si.sortOrder), -1) FROM ScanImage si " +
           "WHERE si.scan.id = :scanId AND si.deletedAt IS NULL")
    int findMaxSortOrderByScanId(@Param("scanId") UUID scanId);

    Optional<ScanImage> findByIdAndScan_IdAndDeletedAtIsNull(
            UUID imageId,
            UUID scanId
    );
}
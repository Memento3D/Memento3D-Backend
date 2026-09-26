package com.memeto.team.memeto3d.repository;

import com.memeto.team.memeto3d.entity.ScanImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScanImageRepository extends JpaRepository<ScanImage, UUID> {

    /**
     * Returns all non-deleted images belonging to a scan, ordered by sort_order ASC then created_at ASC.
     * Includes failed images (upload_status = 'failed') per UC-03 spec.
     */
    @EntityGraph(attributePaths = {"scan"})
    @Query("SELECT si FROM ScanImage si WHERE si.scan.id = :scanId AND si.deletedAt IS NULL " +
           "ORDER BY si.sortOrder ASC, si.createdAt ASC")
    Page<ScanImage> findByScanIdAndDeletedAtIsNull(@Param("scanId") UUID scanId, Pageable pageable);

    /**
     * Counts non-deleted images for a scan (all upload_status values included).
     */
    @Query("SELECT COUNT(si) FROM ScanImage si WHERE si.scan.id = :scanId AND si.deletedAt IS NULL")
    long countByScanIdAndDeletedAtIsNull(@Param("scanId") UUID scanId);
}

package com.memento3d.assets.infrastructure.persistence.repository;

import com.memento3d.scans.domain.model.Scan;

import com.memento3d.assets.domain.model.UploadChunk;
import com.memento3d.assets.domain.model.UploadChunkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UploadChunkRepository extends JpaRepository<UploadChunk, UUID> {

    /**
     * Returns all chunks for a given upload session ordered by chunk_index.
     * An "upload session" is uniquely identified by (scan_id, file_reference).
     */
    List<UploadChunk> findByScanIdAndFileReferenceOrderByChunkIndex(
            UUID scanId, String fileReference);

    /**
     * Returns the chunk_index values of chunks that have a specific status,
     * ordered ascending. Used to build the missing_indexes list (status=uploaded
     * chunks are the ones already received; everything else is "missing").
     */
    @Query("""
        SELECT uc.chunkIndex
          FROM UploadChunk uc
         WHERE uc.scan.id = :scanId
           AND uc.fileReference = :fileReference
           AND uc.status = :status
         ORDER BY uc.chunkIndex ASC
    """)
    List<Integer> findChunkIndexesByScanIdAndFileReferenceAndStatus(
            @Param("scanId") UUID scanId,
            @Param("fileReference") String fileReference,
            @Param("status") UploadChunkStatus status);

    /**
     * Counts chunks with a given status for an upload session.
     */
    long countByScanIdAndFileReferenceAndStatus(
            UUID scanId, String fileReference, UploadChunkStatus status);

    /**
     * Returns true when at least one chunk record exists for the given session.
     * Used to distinguish "session not found" (404) from "session found but empty".
     */
    boolean existsByScanIdAndFileReference(UUID scanId, String fileReference);

    /**
     * Returns the most recently created {@code file_reference} for a scan.
     * Used as a fallback when the client does not supply a {@code file_reference}
     * query parameter and there is only one active upload session for the scan.
     */
    @Query("""
        SELECT uc.fileReference
          FROM UploadChunk uc
         WHERE uc.scan.id = :scanId
         ORDER BY uc.createdAt DESC
         LIMIT 1
    """)
    java.util.Optional<String> findLatestFileReferenceByScanId(@Param("scanId") UUID scanId);
}

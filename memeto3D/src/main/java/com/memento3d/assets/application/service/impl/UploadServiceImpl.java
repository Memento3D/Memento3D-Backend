package com.memento3d.assets.application.service.impl;

import com.memento3d.assets.api.response.UploadStatusResponse;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.assets.domain.model.UploadChunk;
import com.memento3d.assets.domain.model.UploadChunkStatus;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.assets.infrastructure.persistence.repository.UploadChunkRepository;
import com.memento3d.assets.application.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadServiceImpl implements UploadService {

    private final ScanRepository scanRepository;
    private final UploadChunkRepository uploadChunkRepository;

    @Override
    @Transactional(readOnly = true)
    public UploadStatusResponse getUploadStatus(UUID scanId,
                                                 String fileReference,
                                                 UUID currentUserId,
                                                 Role currentUserRole) {

        log.info("getUploadStatus: scanId={}, fileReference={}, userId={}, role={}",
                scanId, fileReference, currentUserId, currentUserRole);

        // ── 1. Resolve scan (IDOR: non-owners and deleted scans → 404) ──────────
        Scan scan = scanRepository.findById(scanId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Upload session not found with ID: " + scanId));

        boolean isPrivileged = Role.ROLE_ADMIN.equals(currentUserRole)
                || Role.ROLE_MANAGER.equals(currentUserRole)
                || Role.ROLE_OPERATOR.equals(currentUserRole);
        if (!isPrivileged && !scan.getUser().getId().equals(currentUserId)) {
            // IDOR: expose as 404, not 403
            throw new ResourceNotFoundException(
                    "Upload session not found with ID: " + scanId);
        }

        // ── 2. Resolve fileReference ─────────────────────────────────────────────
        String resolvedRef = resolveFileReference(scanId, fileReference);

        // ── 3. Verify the session exists ──────────────────────────────────────────
        if (!uploadChunkRepository.existsByScanIdAndFileReference(scanId, resolvedRef)) {
            throw new ResourceNotFoundException(
                    "No upload session found for scan " + scanId
                            + " with file_reference \"" + resolvedRef + "\"");
        }

        // ── 4. Fetch all chunk records for this session ───────────────────────────
        List<UploadChunk> allChunks = uploadChunkRepository
                .findByScanIdAndFileReferenceOrderByChunkIndex(scanId, resolvedRef);

        int chunkTotal = allChunks.size();

        // ── 5. Derive upload counts and missing indexes ───────────────────────────
        Set<Integer> uploadedIndexes = allChunks.stream()
                .filter(c -> UploadChunkStatus.uploaded.equals(c.getStatus()))
                .map(UploadChunk::getChunkIndex)
                .collect(Collectors.toSet());

        int chunkUploaded = uploadedIndexes.size();

        // missing = all known chunk indexes that are NOT in uploadedIndexes
        List<Integer> missingIndexes = allChunks.stream()
                .map(UploadChunk::getChunkIndex)
                .filter(idx -> !uploadedIndexes.contains(idx))
                .sorted()
                .collect(Collectors.toList());

        // ── 6. Derive overall session status ──────────────────────────────────────
        String uploadStatus = deriveSessionStatus(allChunks, chunkTotal, chunkUploaded);

        // ── 7. Build response (completed sessions have no missing_indexes) ────────
        boolean isCompleted = "completed".equals(uploadStatus);

        return UploadStatusResponse.builder()
                .id(scanId)
                .fileReference(resolvedRef)
                .chunkTotal(chunkTotal)
                .chunkUploaded(chunkUploaded)
                .uploadStatus(uploadStatus)
                .missingIndexes(isCompleted ? null : missingIndexes)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Resolves which {@code file_reference} to use.
     *
     * <ul>
     *   <li>If the caller supplied one explicitly, use it as-is.</li>
     *   <li>Otherwise fall back to the most-recently created record's
     *       {@code file_reference} for this scan.</li>
     * </ul>
     */
    private String resolveFileReference(UUID scanId, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        // Fall back: return the most-recently created file_reference for this scan.
        // If nothing exists the subsequent existsByScanIdAndFileReference check will
        // produce a 404 via the empty-string sentinel.
        return uploadChunkRepository
                .findLatestFileReferenceByScanId(scanId)
                .orElse("");
    }

    /**
     * Derives the human-readable session status from the individual chunk states.
     *
     * <table>
     *   <tr><th>Condition</th><th>Status</th></tr>
     *   <tr><td>All chunks uploaded</td><td>completed</td></tr>
     *   <tr><td>Any chunk cancelled</td><td>cancelled</td></tr>
     *   <tr><td>All chunks failed</td><td>failed</td></tr>
     *   <tr><td>Mix of uploaded/pending/failed</td><td>in_progress</td></tr>
     * </table>
     */
    private String deriveSessionStatus(List<UploadChunk> chunks,
                                        int chunkTotal,
                                        int chunkUploaded) {
        if (chunkTotal == 0) {
            return "in_progress";
        }
        if (chunkUploaded == chunkTotal) {
            return "completed";
        }
        boolean anyCancelled = chunks.stream()
                .anyMatch(c -> UploadChunkStatus.cancelled.equals(c.getStatus()));
        if (anyCancelled) {
            return "cancelled";
        }
        long failedCount = chunks.stream()
                .filter(c -> UploadChunkStatus.failed.equals(c.getStatus()))
                .count();
        if (failedCount == chunkTotal) {
            return "failed";
        }
        return "in_progress";
    }
}

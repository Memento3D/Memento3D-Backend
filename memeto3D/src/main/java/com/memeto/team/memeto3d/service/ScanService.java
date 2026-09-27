package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanImageListResponse;
import com.memeto.team.memeto3d.dto.ScanImageUploadResponse;
import com.memeto.team.memeto3d.dto.ScanListResponse;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.dto.UpdateScanRequest;
import com.memeto.team.memeto3d.entity.Role;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ScanService {

    ScanResponse createScan(CreateScanRequest request, UUID userId);

    ScanListResponse getScans(UUID userId, String status, int page, int limit, String sort);

    ScanResponse getScanById(UUID id, UUID currentUserId, Role currentUserRole);

    ScanResponse updateScan(UUID id, UpdateScanRequest request, UUID currentUserId, Role currentUserRole);

    void deleteScan(UUID id, UUID currentUserId, Role currentUserRole);

    /**
     * BE1 — Returns a paginated list of all images (incl. failed) for a scan,
     * sorted by sort_order ASC, created_at ASC. IDOR: 404 on access by non-owner.
     */
    ScanImageListResponse getScanImages(UUID scanId, UUID currentUserId, Role currentUserRole, int page, int limit);

    /**
     * BE2 — Appends a supplemental image to an existing scan (UC-04 "chụp bù").
     * Validates ownership, scan status, file format/size, and storage quota.
     * Sets needs_quality_check=true when a prior QC run exists so the engine
     * only re-checks new images on the next quality check run.
     *
     * @param scanId          target scan
     * @param file            multipart image file (jpg/jpeg/png, ≤ 20 MB)
     * @param currentUserId   authenticated user
     * @param currentUserRole authenticated role (admin/operator bypass IDOR)
     * @return the created ScanImage record + updated scan counters
     */
    ScanImageUploadResponse addImageToScan(UUID scanId, MultipartFile file,
                                           UUID currentUserId, Role currentUserRole);

    /**
     * BE3 — Deletes a specific image from a scan (UC-05 xoá ảnh).
     * Hard-deletes the image (not soft-delete) because images don't need long-term retention.
     * Blocks deletion when scan is in 'processing' status (image is being used for reconstruction).
     * Cascading FK constraints will auto-delete related image_quality_results.
     * Updates scan counters and user storage quota.
     *
     * @param scanId          target scan
     * @param imageId         image to delete
     * @param currentUserId   authenticated user
     * @param currentUserRole authenticated role (admin/operator bypass IDOR)
     * @throws ResourceNotFoundException if scan/image not found or access denied (404)
     * @throws ScanConflictException if scan status is 'processing' (409)
     */
    void deleteImageFromScan(UUID scanId, UUID imageId, UUID currentUserId, Role currentUserRole);
}

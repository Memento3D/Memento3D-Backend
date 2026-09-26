package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanImageListResponse;
import com.memeto.team.memeto3d.dto.ScanListResponse;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.dto.UpdateScanRequest;
import com.memeto.team.memeto3d.entity.Role;

import java.util.UUID;

public interface ScanService {

    ScanResponse createScan(CreateScanRequest request, UUID userId);

    ScanListResponse getScans(UUID userId, String status, int page, int limit, String sort);

    ScanResponse getScanById(UUID id, UUID currentUserId, Role currentUserRole);

    ScanResponse updateScan(UUID id, UpdateScanRequest request, UUID currentUserId, Role currentUserRole);

    void deleteScan(UUID id, UUID currentUserId, Role currentUserRole);

    /**
     * Returns a paginated list of all images (including failed ones) belonging to
     * the given scan session, sorted by sort_order ASC then created_at ASC.
     * Performs ownership check: non-privileged users can only access their own scans (404 on mismatch).
     *
     * @param scanId          the scan session ID
     * @param currentUserId   authenticated user's ID
     * @param currentUserRole authenticated user's role (admin/operator bypass ownership check)
     * @param page            1-based page number
     * @param limit           page size (1-100)
     * @return paginated image list with total_count
     */
    ScanImageListResponse getScanImages(UUID scanId, UUID currentUserId, Role currentUserRole, int page, int limit);
}

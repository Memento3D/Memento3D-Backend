package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.ImageQualityDetailResponse;
import com.memeto.team.memeto3d.entity.Role;

import java.util.UUID;

public interface QualityRunService {

    /**
     * Retrieves full technical quality inspection details for a specific image in a quality check run.
     *
     * @param runId          Quality check run ID
     * @param imageId        Scan image ID
     * @param currentUserId  Authenticated user ID
     * @param currentUserRole Authenticated user role
     * @return Full technical quality result details for image zooming, histogram rendering, and failure remediation.
     */
    ImageQualityDetailResponse getImageQualityDetail(UUID runId, UUID imageId, UUID currentUserId, Role currentUserRole);
}

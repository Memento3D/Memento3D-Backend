package com.memento3d.quality.application.service;

import com.memento3d.identity.domain.model.User;
import com.memento3d.scans.domain.model.Scan;

import com.memento3d.quality.api.response.ImageQualityDetailResponse;
import com.memento3d.identity.domain.model.Role;

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

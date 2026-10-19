package com.memento3d.modelreview.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.modelreview.domain.model.ModelVersion;

import java.util.List;
import java.util.UUID;

public interface ModelReviewService {
    List<ModelVersion> getVersionsForScan(UUID scanId, UUID currentUserId, Role currentUserRole);
    ModelVersion approveVersion(UUID versionId, String notes, UUID currentUserId, Role currentUserRole);
    ModelVersion requestRevision(UUID versionId, String reason, String notes, UUID currentUserId, Role currentUserRole);
    ModelVersion createModelVersion(UUID scanId, UUID jobId, String modelUrl);
}

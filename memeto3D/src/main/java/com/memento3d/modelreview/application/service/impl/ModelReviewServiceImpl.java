package com.memento3d.modelreview.application.service.impl;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.modelreview.application.service.ModelReviewService;
import com.memento3d.modelreview.domain.model.ModelReview;
import com.memento3d.modelreview.domain.model.ModelVersion;
import com.memento3d.modelreview.infrastructure.persistence.repository.ModelReviewRepository;
import com.memento3d.modelreview.infrastructure.persistence.repository.ModelVersionRepository;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelReviewServiceImpl implements ModelReviewService {

    private final ModelVersionRepository modelVersionRepository;
    private final ModelReviewRepository modelReviewRepository;
    private final ScanRepository scanRepository;
    private final ReconstructionJobRepository jobRepository;
    private final OutboxPublisherService outboxPublisher;

    private static final int MAX_REVISION_QUOTA = 3;

    @Override
    @Transactional(readOnly = true)
    public List<ModelVersion> getVersionsForScan(UUID scanId, UUID currentUserId, Role currentUserRole) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found: " + scanId));

        validateScanOwnership(scan, currentUserId, currentUserRole);
        return modelVersionRepository.findByScan_IdOrderByVersionNumberDesc(scanId);
    }

    @Override
    @Transactional
    public ModelVersion approveVersion(UUID versionId, String notes, UUID currentUserId, Role currentUserRole) {
        ModelVersion version = modelVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Model version not found: " + versionId));

        validateScanOwnership(version.getScan(), currentUserId, currentUserRole);

        if (version.isLocked()) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_LOCKED", "Locked model versions cannot be modified or re-approved.");
        }

        if (version.isApproved()) {
            log.info("Model version {} is already approved, returning idempotently", versionId);
            return version;
        }

        version.setStatus("APPROVED");
        ModelVersion savedVersion = modelVersionRepository.save(version);

        ModelReview review = ModelReview.builder()
                .modelVersion(savedVersion)
                .userId(currentUserId)
                .decision("APPROVED")
                .notes(notes)
                .build();
        modelReviewRepository.save(review);

        outboxPublisher.publish(
                "MODEL_VERSION",
                savedVersion.getId().toString(),
                "MODEL_APPROVED",
                Map.of(
                        "versionId", savedVersion.getId(),
                        "scanId", savedVersion.getScan().getId(),
                        "versionNumber", savedVersion.getVersionNumber(),
                        "userId", currentUserId
                )
        );

        log.info("User {} approved model version {}", currentUserId, versionId);
        return savedVersion;
    }

    @Override
    @Transactional
    public ModelVersion requestRevision(UUID versionId, String reason, String notes, UUID currentUserId, Role currentUserRole) {
        ModelVersion version = modelVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Model version not found: " + versionId));

        validateScanOwnership(version.getScan(), currentUserId, currentUserRole);

        if (version.isLocked()) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_LOCKED", "Locked model versions cannot have revisions requested.");
        }

        long existingRevisions = modelReviewRepository.countByModelVersion_Scan_IdAndDecision(
                version.getScan().getId(), "REVISION_REQUESTED"
        );
        if (existingRevisions >= MAX_REVISION_QUOTA) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "REVISION_QUOTA_EXCEEDED",
                    "Revision limit exceeded for this 3D model (maximum " + MAX_REVISION_QUOTA + " revisions)."
            );
        }

        version.setStatus("REVISION_REQUESTED");
        ModelVersion savedVersion = modelVersionRepository.save(version);

        ModelReview review = ModelReview.builder()
                .modelVersion(savedVersion)
                .userId(currentUserId)
                .decision("REVISION_REQUESTED")
                .revisionReason(reason)
                .notes(notes)
                .build();
        modelReviewRepository.save(review);

        outboxPublisher.publish(
                "MODEL_VERSION",
                savedVersion.getId().toString(),
                "REVISION_REQUESTED",
                Map.of(
                        "versionId", savedVersion.getId(),
                        "scanId", savedVersion.getScan().getId(),
                        "versionNumber", savedVersion.getVersionNumber(),
                        "reason", reason != null ? reason : "Unspecified",
                        "userId", currentUserId
                )
        );

        log.info("User {} requested revision for model version {}: {}", currentUserId, versionId, reason);
        return savedVersion;
    }

    @Override
    @Transactional
    public ModelVersion createModelVersion(UUID scanId, UUID jobId, String modelUrl) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found: " + scanId));

        ReconstructionJob job = null;
        if (jobId != null) {
            job = jobRepository.findById(jobId).orElse(null);
        }

        int nextVersionNumber = 1;
        Optional<ModelVersion> latest = modelVersionRepository.findFirstByScan_IdOrderByVersionNumberDesc(scanId);
        if (latest.isPresent()) {
            nextVersionNumber = latest.get().getVersionNumber() + 1;
        }

        ModelVersion version = ModelVersion.builder()
                .scan(scan)
                .job(job)
                .versionNumber(nextVersionNumber)
                .modelUrl(modelUrl)
                .status("AWAITING_APPROVAL")
                .build();

        return modelVersionRepository.save(version);
    }

    private void validateScanOwnership(Scan scan, UUID currentUserId, Role currentUserRole) {
        boolean isStaff = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_MANAGER;
        if (!isStaff && !scan.getUser().getId().equals(currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN_SCAN_ACCESS", "You do not own this model/scan.");
        }
    }
}

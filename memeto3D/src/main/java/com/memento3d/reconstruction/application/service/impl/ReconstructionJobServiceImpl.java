package com.memento3d.reconstruction.application.service.impl;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.reconstruction.domain.model.ExecutionMode;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.scans.domain.model.InputMode;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanImage;
import com.memento3d.scans.domain.model.ScanImageUploadStatus;
import com.memento3d.scans.domain.model.ScanStatus;

import com.memento3d.reconstruction.api.request.CreateReconstructionJobRequest;
import com.memento3d.reconstruction.api.response.ReconstructionJobResponse;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.scans.domain.exception.ScanConflictException;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.scans.infrastructure.persistence.repository.ScanImageRepository;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.reconstruction.application.service.ReconstructionJobService;
import com.memento3d.scans.application.service.ScanAccessGuard;
import com.memento3d.scans.application.service.ScanStatusCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconstructionJobServiceImpl
        implements ReconstructionJobService {

    private static final Set<ReconstructionJobStatus> ACTIVE_STATUSES =
            Set.of(
                    ReconstructionJobStatus.queued,
                    ReconstructionJobStatus.running
            );

    private final ScanRepository scanRepository;
    private final ScanImageRepository scanImageRepository;
    private final ReconstructionJobRepository jobRepository;
    private final ScanAccessGuard scanAccessGuard;
    private final ScanStatusCacheService scanStatusCacheService;
    private final com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository modelOutputRepository;

    @Value("${app.ai.execution-mode:real}")
    private String configuredExecutionMode;

    @Override
    @Transactional
    public ReconstructionJobResponse submitJob(
            UUID scanId,
            CreateReconstructionJobRequest request,
            UUID currentUserId,
            Role currentUserRole
    ) {
        // Kiểm tra quyền trước khi lấy khóa ghi.
        Scan existingScan = findAccessibleScan(
                scanId,
                currentUserId,
                currentUserRole
        );

        Scan scan = scanRepository.findByIdForUpdate(existingScan.getId())
                .orElseThrow(() -> scanNotFound(scanId));

        /*
         * Refresh vì scan đã được đọc trước khi lấy khóa.
         * Đảm bảo thấy trạng thái mới nhất sau khi chờ transaction khác.
         */
        entityManager.refresh(scan);

        if (scan.getDeletedAt() != null
                || scan.getStatus() == ScanStatus.deleted) {
            throw scanNotFound(scanId);
        }

        scanAccessGuard.requireAccess(
                scan,
                currentUserId,
                currentUserRole
        );

        if (jobRepository.existsByScan_IdAndStatusIn(
                scanId,
                ACTIVE_STATUSES
        )) {
            throw new ScanConflictException(
                    "This scan already has an active reconstruction job"
            );
        }

        // Giai đoạn hiện tại: cho generate sau upload, chưa yêu cầu QC.
        // Giữ hỗ trợ ready cho các scan đã đi qua luồng hiện có.
        if (scan.getStatus() != ScanStatus.uploaded
                && scan.getStatus() != ScanStatus.ready) {
            throw new ScanConflictException(
                    "Scan must be in 'uploaded' or 'ready' status to submit reconstruction"
            );
        }

        if (scan.getInputMode() != InputMode.image) {
            throw new ScanConflictException(
                    "This reconstruction pipeline currently supports image input only"
            );
        }

        if (request == null || request.getInputImageId() == null) {
            throw new IllegalArgumentException(
                    "input_image_id is required"
            );
        }

        String profile = resolveProfile(request.getProfile());

        ScanImage image = scanImageRepository
                .findByIdAndScan_IdAndDeletedAtIsNull(
                        request.getInputImageId(),
                        scanId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Input image not found in this scan"
                ));

        validateInputImage(image);

        ReconstructionJob job = ReconstructionJob.builder()
                .scan(scan)
                .inputImageId(image.getId())
                .inputSnapshot(buildInputSnapshot(image))
                .status(ReconstructionJobStatus.queued)
                .progress(0)
                .profile(profile)
                .executionMode(resolveExecutionMode())
                /*
                 * Chưa nối AI nên chưa có cấu hình toolchain thực tế.
                 * Bước tích hợp sẽ thay bằng cấu hình đã resolve.
                 */
                .toolchainConfig(Map.of())
                .retryCount(0)
                .build();

        jobRepository.saveAndFlush(job);

        scan.setStatus(ScanStatus.processing);
        scanRepository.saveAndFlush(scan);

        updateCacheAfterCommit(scanId);

        log.info(
                "Queued reconstruction job {} for scan {}",
                job.getId(),
                scanId
        );

        return ReconstructionJobResponse.fromEntity(job);
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public ReconstructionJobResponse getLatestJob(
            UUID scanId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        findAccessibleScan(
                scanId,
                currentUserId,
                currentUserRole
        );

        ReconstructionJob job = jobRepository
                .findFirstByScan_IdOrderByCreatedAtDescIdDesc(scanId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reconstruction job not found for scan: " + scanId
                ));

        return ReconstructionJobResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconstructionJobResponse getJobById(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        ReconstructionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconstruction job not found with ID: " + jobId));

        findAccessibleScan(job.getScan().getId(), currentUserId, currentUserRole);
        return ReconstructionJobResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.memento3d.models.api.response.ModelOutputResponse> getJobOutputs(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        ReconstructionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconstruction job not found with ID: " + jobId));

        findAccessibleScan(job.getScan().getId(), currentUserId, currentUserRole);

        return modelOutputRepository.findByJob_IdOrderByCreatedAtDesc(jobId).stream()
                .map(com.memento3d.models.api.response.ModelOutputResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public ReconstructionJobResponse cancelJob(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        ReconstructionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconstruction job not found with ID: " + jobId));

        Scan scan = findAccessibleScan(job.getScan().getId(), currentUserId, currentUserRole);

        if (job.getStatus() == ReconstructionJobStatus.done || job.getStatus() == ReconstructionJobStatus.failed) {
            throw new ScanConflictException("Cannot cancel a completed or failed reconstruction job");
        }

        if (job.getStatus() == ReconstructionJobStatus.cancelled) {
            return ReconstructionJobResponse.fromEntity(job);
        }

        job.setStatus(ReconstructionJobStatus.cancelled);
        jobRepository.save(job);

        scan.setStatus(ScanStatus.ready);
        scanRepository.save(scan);

        updateCacheAfterCommit(scan.getId());
        log.info("Cancelled reconstruction job {} for scan {}", jobId, scan.getId());

        return ReconstructionJobResponse.fromEntity(job);
    }

    private Scan findAccessibleScan(
            UUID scanId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(scanId)
                .orElseThrow(() -> scanNotFound(scanId));

        if (scan.getStatus() == ScanStatus.deleted) {
            throw scanNotFound(scanId);
        }

        scanAccessGuard.requireAccess(
                scan,
                currentUserId,
                currentUserRole
        );

        return scan;
    }

    private String resolveProfile(String requestedProfile) {
        String profile =
                requestedProfile == null ? "default" : requestedProfile;

        if (!"default".equals(profile)) {
            throw new IllegalArgumentException(
                    "Only profile 'default' is currently supported"
            );
        }

        return profile;
    }

    private void validateInputImage(ScanImage image) {
        if (image.getUploadStatus() != ScanImageUploadStatus.uploaded) {
            throw new ScanConflictException(
                    "Input image has not been uploaded successfully"
            );
        }

        if (Boolean.TRUE.equals(image.getIsExcluded())) {
            throw new ScanConflictException(
                    "Excluded image cannot be used for reconstruction"
            );
        }
//          TODO: Bổ sung Quality Check qua interface sau khi
//          hoàn thiện luồng upload → generate → output.
//        if (Boolean.TRUE.equals(image.getNeedsQualityCheck())) {
//            throw new ScanConflictException(
//                    "Input image requires a new quality check"
//            );
//        }

        if (image.getCloudinaryPublicId() == null
                || image.getCloudinaryPublicId().isBlank()) {
            throw new ScanConflictException(
                    "Input image is missing Cloudinary storage metadata"
            );
        }
    }

    private Map<String, Object> buildInputSnapshot(ScanImage image) {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        snapshot.put("image_id", image.getId().toString());
        snapshot.put("cloudinary_public_id", image.getCloudinaryPublicId());
        snapshot.put("cloudinary_asset_id", image.getCloudinaryAssetId());
        snapshot.put("original_filename", image.getOriginalFilename());
        snapshot.put("format", image.getFormat());
        snapshot.put("mime_type", image.getMimeType());
        snapshot.put("file_size_bytes", image.getFileSizeBytes());
        snapshot.put("width", image.getWidth());
        snapshot.put("height", image.getHeight());
        snapshot.put("hash_md5", image.getHashMd5());

        return snapshot;
    }

    private void updateCacheAfterCommit(UUID scanId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            scanStatusCacheService.cacheScanStatus(
                                    scanId,
                                    "PROCESSING"
                            );
                        } catch (Exception ex) {
                            // DB đã commit: không báo submit thất bại vì Redis.
                            log.warn(
                                    "Failed to update scan status cache for {}",
                                    scanId,
                                    ex
                            );
                        }
                    }
                }
        );
    }

    private ResourceNotFoundException scanNotFound(UUID scanId) {
        return new ResourceNotFoundException(
                "Scan not found with ID: " + scanId
        );
    }

    private ExecutionMode resolveExecutionMode() {
        if (configuredExecutionMode == null) {
            throw new IllegalStateException(
                    "AI execution mode is missing"
            );
        }

        return switch (configuredExecutionMode.trim().toLowerCase(
                java.util.Locale.ROOT
        )) {
            case "real" -> ExecutionMode.real;
            case "mock" -> ExecutionMode.mock;
            default -> throw new IllegalStateException(
                    "Unsupported AI execution mode: "
                            + configuredExecutionMode
                            + ". Supported values: real, mock"
            );
        };
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReconstructionJobResponse> listJobs(
            UUID currentUserId,
            Role currentUserRole,
            int page,
            int limit
    ) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = Math.min(Math.max(1, limit), 100);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(pageIndex, pageSize);

        boolean isStaff = currentUserRole == Role.ROLE_ADMIN 
                || currentUserRole == Role.ROLE_MANAGER 
                || currentUserRole == Role.ROLE_OPERATOR;
        org.springframework.data.domain.Page<ReconstructionJob> jobPage;
        if (isStaff) {
            jobPage = jobRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            jobPage = jobRepository.findByScan_User_IdOrderByCreatedAtDesc(currentUserId, pageable);
        }

        return jobPage.map(ReconstructionJobResponse::fromEntity);
    }

}

/*
* Có ba điểm bạn cần hiểu ở service này:
- Job và trạng thái scan lưu trong cùng transaction: nếu một phần lỗi, cả hai rollback.
- Redis cập nhật sau khi database commit: tránh cache hiển thị PROCESSING trong khi việc tạo job đã rollback. Nếu Redis lỗi, database vẫn là nguồn trạng thái chính; cache cũ có thể tồn tại đến khi được cập nhật hoặc hết hạn.
- Snapshot lưu metadata Cloudinary: storagePath hiện tại của bạn là public ID, nên không coi nó là URL tải ảnh. URL sẽ được tạo khi dispatch sang AI.
executionMode = real đang cố định cho bước đầu. toolchainConfig tạm là {}; trước khi nối worker, mình sẽ thêm cấu hình backend để resolve profile và lưu cấu hình thực tế.
* */
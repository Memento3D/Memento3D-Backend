package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.CreateReconstructionJobRequest;
import com.memeto.team.memeto3d.dto.ReconstructionJobResponse;
import com.memeto.team.memeto3d.entity.*;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.exception.ScanConflictException;
import com.memeto.team.memeto3d.repository.ReconstructionJobRepository;
import com.memeto.team.memeto3d.repository.ScanImageRepository;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.service.ReconstructionJobService;
import com.memeto.team.memeto3d.service.ScanAccessGuard;
import com.memeto.team.memeto3d.service.ScanStatusCacheService;
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

}

/*
* Có ba điểm bạn cần hiểu ở service này:
- Job và trạng thái scan lưu trong cùng transaction: nếu một phần lỗi, cả hai rollback.
- Redis cập nhật sau khi database commit: tránh cache hiển thị PROCESSING trong khi việc tạo job đã rollback. Nếu Redis lỗi, database vẫn là nguồn trạng thái chính; cache cũ có thể tồn tại đến khi được cập nhật hoặc hết hạn.
- Snapshot lưu metadata Cloudinary: storagePath hiện tại của bạn là public ID, nên không coi nó là URL tải ảnh. URL sẽ được tạo khi dispatch sang AI.
executionMode = real đang cố định cho bước đầu. toolchainConfig tạm là {}; trước khi nối worker, mình sẽ thêm cấu hình backend để resolve profile và lưu cấu hình thực tế.
* */
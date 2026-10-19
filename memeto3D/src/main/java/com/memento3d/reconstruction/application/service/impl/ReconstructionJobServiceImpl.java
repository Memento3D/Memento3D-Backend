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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.memento3d.reconstruction.domain.model.ReconstructionStage;
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
    private final com.memento3d.reconstruction.infrastructure.persistence.repository.PreviewQuotaRepository previewQuotaRepository;
    private final com.memento3d.identity.infrastructure.persistence.repository.UserRepository userRepository;
    private final com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;
    private final com.memento3d.orders.infrastructure.persistence.repository.OrderRepository orderRepository;
    private final com.memento3d.entitlement.application.service.EntitlementService entitlementService;

    @Value("${app.ai.execution-mode:real}")
    private String configuredExecutionMode = "real";

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
        if (entityManager != null) {
            entityManager.refresh(scan);
        }

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

        if (!"default".equals(profile) && !"high_quality_detailed".equals(profile) && !"preview".equals(profile)) {
            throw new IllegalArgumentException(
                    "Only profile 'default', 'preview', or 'high_quality_detailed' is currently supported"
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
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                if (scanStatusCacheService != null) {
                    scanStatusCacheService.cacheScanStatus(scanId, "PROCESSING");
                }
            } catch (Exception ex) {
                log.warn("Failed to update scan status cache for {}", scanId, ex);
            }
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            if (scanStatusCacheService != null) {
                                scanStatusCacheService.cacheScanStatus(
                                        scanId,
                                        "PROCESSING"
                                );
                            }
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

    @Override
    @Transactional
    public ReconstructionJobResponse submitPreviewJob(
            com.memento3d.reconstruction.api.request.CreatePreviewJobRequest request,
            UUID currentUserId,
            Role currentUserRole
    ) {
        log.info("Submitting preview reconstruction job for scan {} by user {}", request.getScanId(), currentUserId);

        // 1. Pessimistic lock on PreviewQuota
        com.memento3d.reconstruction.domain.model.PreviewQuota quota = previewQuotaRepository
                .findByUserIdForUpdate(currentUserId)
                .orElseGet(() -> {
                    com.memento3d.identity.domain.model.User user = userRepository.findById(currentUserId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));
                    return previewQuotaRepository.save(com.memento3d.reconstruction.domain.model.PreviewQuota.builder()
                            .user(user)
                            .totalQuota(3)
                            .usedQuota(0)
                            .build());
                });

        if (!quota.hasAvailableQuota()) {
            throw new com.memento3d.reconstruction.domain.exception.QuotaExceededException(
                    "You have used all " + quota.getTotalQuota() + " free preview generations. Please purchase a keepsake to reconstruct further models."
            );
        }

        // 2. Delegate to standard submitJob with tier PREVIEW
        CreateReconstructionJobRequest jobRequest = CreateReconstructionJobRequest.builder()
                .inputImageId(request.getInputImageId())
                .profile(request.getProfile() != null ? request.getProfile() : "preview_fast")
                .build();

        ReconstructionJobResponse response = submitJob(request.getScanId(), jobRequest, currentUserId, currentUserRole);

        // 3. Set tier to PREVIEW on the persisted entity
        ReconstructionJob job = jobRepository.findById(response.getJobId())
                .orElseThrow();
        job.setTier(com.memento3d.reconstruction.domain.model.ReconstructionTier.PREVIEW);
        jobRepository.save(job);

        // 4. Atomically increment used quota
        quota.incrementUsed();
        previewQuotaRepository.save(quota);

        // 5. Outbox Event in the same transaction
        outboxPublisher.publish(
                "RECONSTRUCTION_JOB",
                job.getId().toString(),
                "PREVIEW_JOB_CREATED",
                Map.of(
                        "jobId", job.getId(),
                        "scanId", request.getScanId(),
                        "userId", currentUserId,
                        "tier", "PREVIEW"
                )
        );

        return ReconstructionJobResponse.fromEntity(job);
    }

    @Override
    @Transactional
    public ReconstructionJobResponse submitPremiumJob(
            UUID orderId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        log.info("Submitting premium reconstruction job for order {} by user {}", orderId, currentUserId);

        com.memento3d.orders.domain.model.Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        boolean isStaff = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_MANAGER;
        if (!isStaff && !order.getUserId().equals(currentUserId)) {
            throw new com.memento3d.shared.exception.BusinessException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "FORBIDDEN_ORDER_ACCESS",
                    "You do not own this order."
            );
        }

        // Verify order is paid
        if (order.getPaymentStatus() != com.memento3d.payment.domain.model.PaymentStatus.PAID
                && order.getPaymentStatus() != com.memento3d.payment.domain.model.PaymentStatus.SUCCESS) {
            throw new com.memento3d.shared.exception.BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "PAYMENT_REQUIRED",
                    "Order must be paid before submitting high quality reconstruction."
            );
        }

        UUID scanId = order.getScanId();
        if (scanId == null) {
            throw new com.memento3d.shared.exception.BusinessException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "NO_SCAN_ASSOCIATED",
                    "Order does not have a 3D scan associated with it."
            );
        }

        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Associated scan not found: " + scanId));

        // Find primary image for scan
        ScanImage image = scanImageRepository.findFirstByScan_IdAndDeletedAtIsNullOrderByCreatedAtDesc(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("No valid image found for this scan."));

        CreateReconstructionJobRequest jobRequest = CreateReconstructionJobRequest.builder()
                .inputImageId(image.getId())
                .profile("high_quality_detailed")
                .build();

        ReconstructionJobResponse response = submitJob(scanId, jobRequest, currentUserId, currentUserRole);

        ReconstructionJob job = jobRepository.findById(response.getJobId()).orElseThrow();
        job.setTier(com.memento3d.reconstruction.domain.model.ReconstructionTier.HIGH_QUALITY);
        job.setOrderId(orderId);
        jobRepository.save(job);

        // Record outbox event
        outboxPublisher.publish(
                "RECONSTRUCTION_JOB",
                job.getId().toString(),
                "HIGH_QUALITY_JOB_CREATED",
                Map.of(
                        "jobId", job.getId(),
                        "orderId", orderId,
                        "scanId", scanId,
                        "userId", currentUserId,
                        "tier", "HIGH_QUALITY"
                )
        );

        return ReconstructionJobResponse.fromEntity(job);
    }

    @Override
    @Transactional(readOnly = true)
    public com.memento3d.reconstruction.api.response.PreviewJobDetailsResponse getPreviewDetails(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        ReconstructionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconstruction job not found with ID: " + jobId));

        findAccessibleScan(job.getScan().getId(), currentUserId, currentUserRole);

        // Fetch outputs
        List<com.memento3d.models.domain.model.ModelOutput> outputs = modelOutputRepository.findByJob_IdOrderByCreatedAtDesc(jobId);
        String previewUrl = null;
        if (!outputs.isEmpty()) {
            previewUrl = outputs.get(0).getDeliveryUrl();
        }

        return com.memento3d.reconstruction.api.response.PreviewJobDetailsResponse.fromEntity(job, previewUrl);
    }

    @Override
    @Transactional
    public com.memento3d.reconstruction.api.response.QuotaResponse getMyQuota(UUID currentUserId) {
        com.memento3d.reconstruction.domain.model.PreviewQuota quota = previewQuotaRepository
                .findByUser_Id(currentUserId)
                .orElseGet(() -> {
                    com.memento3d.identity.domain.model.User user = userRepository.findById(currentUserId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));
                    return previewQuotaRepository.save(com.memento3d.reconstruction.domain.model.PreviewQuota.builder()
                            .user(user)
                            .totalQuota(3)
                            .usedQuota(0)
                            .build());
                });

        return com.memento3d.reconstruction.api.response.QuotaResponse.builder()
                .totalQuota(quota.getTotalQuota())
                .usedQuota(quota.getUsedQuota())
                .remainingQuota(quota.getRemainingQuota())
                .build();
    }

    @Override
    @Transactional
    public ReconstructionJobResponse retryJob(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        ReconstructionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconstruction job not found: " + jobId));

        boolean isStaff = currentUserRole == Role.ROLE_ADMIN || currentUserRole == Role.ROLE_MANAGER;
        if (!isStaff) {
            findAccessibleScan(job.getScan().getId(), currentUserId, currentUserRole);
        }

        job.setStatus(ReconstructionJobStatus.queued);
        job.setStage(null);
        job.setProgress(0);
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        jobRepository.save(job);

        outboxPublisher.publish(
                "RECONSTRUCTION_JOB",
                job.getId().toString(),
                "JOB_RETRIED",
                Map.of(
                        "jobId", job.getId(),
                        "retryCount", job.getRetryCount()
                )
        );

        return ReconstructionJobResponse.fromEntity(job);
    }
}

/*
* Có ba điểm bạn cần hiểu ở service này:
- Job và trạng thái scan lưu trong cùng transaction: nếu một phần lỗi, cả hai rollback.
- Redis cập nhật sau khi database commit: tránh cache hiển thị PROCESSING trong khi việc tạo job đã rollback. Nếu Redis lỗi, database vẫn là nguồn trạng thái chính; cache cũ có thể tồn tại đến khi được cập nhật hoặc hết hạn.
- Snapshot lưu metadata Cloudinary: storagePath hiện tại của bạn là public ID, nên không coi nó là URL tải ảnh. URL sẽ được tạo khi dispatch sang AI.
executionMode = real đang cố định cho bước đầu. toolchainConfig tạm là {}; trước khi nối worker, mình sẽ thêm cấu hình backend để resolve profile và lưu cấu hình thực tế.
* */
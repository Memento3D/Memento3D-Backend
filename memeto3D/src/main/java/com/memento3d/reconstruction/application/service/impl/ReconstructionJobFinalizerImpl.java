package com.memento3d.reconstruction.application.service.impl;

import com.memento3d.assets.infrastructure.storage.CloudinaryStorageService;
import com.memento3d.models.domain.model.ModelOutput;
import com.memento3d.reconstruction.application.service.ReconstructionJobFinalizer;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionStage;
import com.memento3d.scans.application.service.ScanStatusCacheService;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanStatus;

import com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ReconstructionJobFinalizerImpl
        implements ReconstructionJobFinalizer {

    private final ReconstructionJobRepository jobRepository;
    private final ModelOutputRepository outputRepository;
    private final AiGenerationClient aiClient;
    private final CloudinaryStorageService storageService;
    private final ScanStatusCacheService cacheService;
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transaction;
    private final com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository digitalAssetRepository;
    private final com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;

    public ReconstructionJobFinalizerImpl(
            ReconstructionJobRepository jobRepository,
            ModelOutputRepository outputRepository,
            AiGenerationClient aiClient,
            CloudinaryStorageService storageService,
            ScanStatusCacheService cacheService,
            PlatformTransactionManager transactionManager,
            com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository digitalAssetRepository,
            com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher
    ) {
        this.jobRepository = jobRepository;
        this.outputRepository = outputRepository;
        this.aiClient = aiClient;
        this.storageService = storageService;
        this.cacheService = cacheService;
        this.transactionManager = transactionManager;
        this.transaction = new TransactionTemplate(transactionManager);
        this.digitalAssetRepository = digitalAssetRepository;
        this.outboxPublisher = outboxPublisher;
    }

    private record Context(UUID userId, UUID scanId) {
    }

    @Override
    public void finish(UUID jobId, UUID attemptId) {
        Context context = transaction.execute(tx -> {
            ReconstructionJob job = jobRepository
                    .findByIdForUpdate(jobId)
                    .orElseThrow();

            if (!canFinalize(job, attemptId)) {
                return null;
            }

            if (job.getScan().getDeletedAt() != null) {
                throw new IllegalStateException(
                        "Cannot finalize a deleted scan"
                );
            }

            job.setStage(ReconstructionStage.uploading);
            job.setProgress(null);

            return new Context(
                    job.getScan().getUser().getId(),
                    job.getScan().getId()
            );
        });

        if (context == null) {
            return;
        }

        /*
         * HTTP và upload nằm ngoài transaction.
         * Nếu lỗi, scheduler sẽ thử lại; không generate lại model.
         */
        byte[] bytes = aiClient.downloadGlb(jobId, attemptId);
        String hash = sha256(bytes);

        Map<String, Object> uploaded;

        try {
            uploaded = storageService.uploadGlb(
                    bytes,
                    context.userId(),
                    context.scanId(),
                    jobId,
                    attemptId
            );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to upload GLB to Cloudinary",
                    ex
            );
        }

        String publicId = requiredString(uploaded, "public_id");
        String assetId = requiredString(uploaded, "asset_id");
        String url = requiredString(uploaded, "secure_url");

        Object uploadedBytes = uploaded.get("bytes");

        if (!(uploadedBytes instanceof Number size)
                || size.longValue() != bytes.length) {
            throw new IllegalStateException(
                    "Cloudinary returned an unexpected file size"
            );
        }

        UUID completedScanId = transaction.execute(tx -> {
            ReconstructionJob job = jobRepository
                    .findByIdForUpdate(jobId)
                    .orElseThrow();

            // Job có thể đã bị hủy hoặc thay attempt trong lúc upload.
            if (!canFinalize(job, attemptId)
                    || job.getScan().getDeletedAt() != null) {
                return null;
            }

            ModelOutput existing = outputRepository
                    .findByJob_IdAndAttemptIdAndFormat(
                            jobId,
                            attemptId,
                            "glb"
                    )
                    .orElse(null);

            if (existing == null) {
                outputRepository.save(
                        ModelOutput.builder()
                                .job(job)
                                .attemptId(attemptId)
                                .format("glb")
                                .storageProvider("cloudinary")
                                .storagePublicId(publicId)
                                .storageAssetId(assetId)
                                .deliveryUrl(url)
                                .fileSizeBytes((long) bytes.length)
                                .sha256(hash)
                                .build()
                );
            } else if (!hash.equalsIgnoreCase(existing.getSha256())) {
                throw new IllegalStateException(
                        "Existing output has a different SHA-256"
                );
            }

            job.setStatus(ReconstructionJobStatus.done);
            job.setStage(null);
            job.setProgress(100);
            job.setCompletedAt(Instant.now());
            job.setErrorCode(null);
            job.setErrorMessage(null);

            job.getScan().setStatus(ScanStatus.done);

            // Output và trạng thái job/scan commit cùng nhau.
            jobRepository.saveAndFlush(job);
            outputRepository.flush();

            if (job.getTier() == com.memento3d.reconstruction.domain.model.ReconstructionTier.HIGH_QUALITY) {
                digitalAssetRepository.save(
                        com.memento3d.assets.domain.model.DigitalAsset.builder()
                                .userId(job.getScan().getUser().getId())
                                .orderItemId(job.getOrderItemId())
                                .job(job)
                                .fileFormat("GLB")
                                .storageProvider("CLOUDINARY")
                                .storagePath(publicId)
                                .deliveryUrl(url)
                                .fileSizeBytes((long) bytes.length)
                                .sha256(hash)
                                .status("READY")
                                .downloadCount(0)
                                .build()
                );

                outboxPublisher.publish(
                        "MODEL_REVIEW",
                        job.getId().toString(),
                        "MODEL_APPROVAL_REQUIRED",
                        Map.of(
                                "jobId", job.getId(),
                                "orderItemId", job.getOrderItemId() != null ? job.getOrderItemId() : "",
                                "userId", job.getScan().getUser().getId(),
                                "modelUrl", url,
                                "sha256", hash
                        )
                );
            } else {
                outboxPublisher.publish(
                        "RECONSTRUCTION",
                        job.getId().toString(),
                        "PREVIEW_READY",
                        Map.of(
                                "jobId", job.getId(),
                                "scanId", job.getScan().getId(),
                                "userId", job.getScan().getUser().getId(),
                                "previewUrl", url,
                                "tier", "PREVIEW"
                        )
                );
            }

            return job.getScan().getId();
        });

        if (completedScanId != null) {
            try {
                cacheService.cacheScanStatus(
                        completedScanId,
                        "COMPLETED"
                );
            } catch (Exception ex) {
                log.warn(
                        "Output saved but scan cache update failed: {}",
                        completedScanId,
                        ex
                );
            }

            log.info(
                    "Reconstruction job {} completed and output saved",
                    jobId
            );
        }
    }

    private boolean canFinalize(
            ReconstructionJob job,
            UUID attemptId
    ) {
        return job.getStatus() == ReconstructionJobStatus.running
                && attemptId.equals(job.getAttemptId());
    }

    private String requiredString(
            Map<String, Object> result,
            String key
    ) {
        Object value = result.get(key);

        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException(
                    "Cloudinary response is missing " + key
            );
        }

        return text;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(bytes)
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
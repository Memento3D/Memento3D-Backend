package com.memento3d.reconstruction.application.service.impl;

import com.memento3d.scans.domain.model.Scan;

import com.cloudinary.Cloudinary;
import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionStage;
import com.memento3d.reconstruction.domain.exception.AiClientException;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.scans.domain.model.ScanStatus;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.memento3d.reconstruction.domain.model.ExecutionMode;

@Component
@Slf4j
public class ReconstructionJobDispatcher {

    private final ReconstructionJobRepository jobRepository;
    private final ScanRepository scanRepository;
    private final AiGenerationClient aiClient;
    private final Cloudinary cloudinary;
    private final TransactionTemplate transaction;
    private final boolean enabled;

    public ReconstructionJobDispatcher(
            ReconstructionJobRepository jobRepository,
            ScanRepository scanRepository,
            AiGenerationClient aiClient,
            Cloudinary cloudinary,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai.dispatch-enabled:false}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
        this.scanRepository = scanRepository;
        this.aiClient = aiClient;
        this.cloudinary = cloudinary;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.ai.dispatch-delay-ms:10000}")
    public void dispatchNextJob() {
        if (!enabled) {
            return;
        }

        List<ReconstructionJob> waiting =
                jobRepository.findByExecutionModeAndStatusOrderByQueuedAtAsc(
                        ExecutionMode.real,
                        ReconstructionJobStatus.queued,
                        PageRequest.of(0, 1)
                );

        if (waiting.isEmpty()) {
            return;
        }

        UUID jobId = waiting.get(0).getId();

        try {
            AiSubmitJobRequest request = prepareRequest(jobId);

            if (request == null) {
                return;
            }

            // prepareRequest đã commit trước khi gọi HTTP.
            AiJobResponse response = aiClient.submitJob(request);

            /*
             * POST có thể trả queued ngay trước khi background task chạy.
             * Đọc lại để lấy trạng thái hiện tại nếu cần.
             */
            if ("queued".equals(response.status())) {
                response = aiClient.getJob(jobId);
            }

            recordAcceptance(request, response);

        } catch (AiClientException ex) {
            if (isPermanentAiRejection(ex)) {
                failQueuedJob(jobId, "AI_REQUEST_REJECTED", ex.getMessage());
                log.error(
                        "AI rejected reconstruction request: jobId={}, upstreamStatus={}",
                        jobId,
                        ex.getUpstreamStatus()
                );
            } else {
                /*
                 * Giữ queued và giữ nguyên attempt để retry lỗi mạng/AI tạm thời.
                 */
                log.warn(
                        "AI dispatch pending: jobId={}, upstreamStatus={}",
                        jobId,
                        ex.getUpstreamStatus()
                );
            }
        } catch (InvalidAiInputSnapshotException ex) {
            failQueuedJob(jobId, "INVALID_AI_INPUT", ex.getMessage());
            log.warn("AI dispatch rejected invalid input snapshot: jobId={}", jobId);
        } catch (Exception ex) {
            log.error("Failed to dispatch AI job {}", jobId, ex);
        }
    }

    private boolean isPermanentAiRejection(AiClientException exception) {
        Integer status = exception.getUpstreamStatus();
        return "AI_API_KEY is not configured".equals(exception.getMessage())
                || (status != null
                && status >= 400
                && status < 500
                && status != 408
                && status != 429);
    }

    private void failQueuedJob(UUID jobId, String errorCode, String message) {
        transaction.executeWithoutResult(tx -> {
            ReconstructionJob job = jobRepository
                    .findByIdForUpdate(jobId)
                    .orElseThrow();
            if (job.getStatus() != ReconstructionJobStatus.queued) {
                return;
            }

            job.setStatus(ReconstructionJobStatus.failed);
            job.setErrorCode(errorCode);
            job.setErrorMessage(message);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            if (job.getScan().getStatus() == ScanStatus.processing) {
                job.getScan().setStatus(ScanStatus.ready);
                scanRepository.save(job.getScan());
            }
        });
    }

    private AiSubmitJobRequest prepareRequest(UUID jobId) {
        return transaction.execute(tx -> {
            ReconstructionJob job = jobRepository
                    .findByIdForUpdate(jobId)
                    .orElseThrow();

            if (job.getStatus() != ReconstructionJobStatus.queued) {
                return null;
            }

            if (job.getExecutionMode()
                    != com.memento3d.reconstruction.domain.model.ExecutionMode.real) {
                throw new IllegalStateException(
                        "This dispatcher only supports real execution mode"
                );
            }

            if (job.getScan().getDeletedAt() != null) {
                throw new IllegalStateException(
                        "Cannot dispatch a job belonging to a deleted scan"
                );
            }

            if (job.getAttemptId() == null) {
                job.setAttemptId(UUID.randomUUID());
            }

            List<AiSubmitJobRequest.InputImage> inputImages =
                    buildAiInputImages(job.getInputSnapshot());

            jobRepository.saveAndFlush(job);

            return new AiSubmitJobRequest(
                    job.getId(),
                    job.getAttemptId(),
                    inputImages,
                    "default"
            );
        });
    }

    private List<AiSubmitJobRequest.InputImage> buildAiInputImages(
            Map<String, Object> inputSnapshot
    ) {
        Object value = inputSnapshot.get("input_images");
        if (!(value instanceof List<?> rows) || rows.size() < 3 || rows.size() > 4) {
            throw new InvalidAiInputSnapshotException(
                    "Job snapshot must contain three required labeled views and optional back; re-create this job"
            );
        }

        List<AiSubmitJobRequest.InputImage> inputImages = new ArrayList<>(rows.size());
        Set<String> imageIds = new HashSet<>();
        Set<String> viewHints = new HashSet<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> image)) {
                throw new InvalidAiInputSnapshotException(
                        "Job snapshot contains an invalid input image entry; re-create this job"
                );
            }

            Object imageIdValue = image.get("image_id");
            Object publicIdValue = image.get("cloudinary_public_id");
            Object viewHintValue = image.get("view_hint");
            if (!(imageIdValue instanceof String imageIdText)
                    || !(publicIdValue instanceof String publicId)
                    || publicId.isBlank()
                    || !(viewHintValue instanceof String viewHint)
                    || !Set.of("front", "left", "right", "back").contains(viewHint)
                    || !imageIds.add(imageIdText)
                    || !viewHints.add(viewHint)) {
                throw new InvalidAiInputSnapshotException(
                        "Job snapshot contains missing or duplicate image metadata; re-create this job"
                );
            }

            UUID imageId;
            try {
                imageId = UUID.fromString(imageIdText);
            } catch (IllegalArgumentException ex) {
                throw new InvalidAiInputSnapshotException(
                        "Job snapshot contains an invalid image UUID; re-create this job",
                        ex
                );
            }

            /*
             * Images are uploaded as public Cloudinary assets. Generate the
             * HTTPS URL from the persisted public ID instead of storage_path.
             */
            String inputUrl = cloudinary.url()
                    .secure(true)
                    .resourceType("image")
                    .type("upload")
                    .generate(publicId);

            inputImages.add(new AiSubmitJobRequest.InputImage(
                    imageId,
                    inputUrl,
                    viewHint
            ));
        }

        if (!viewHints.containsAll(Set.of("front", "left", "right"))) {
            throw new InvalidAiInputSnapshotException(
                    "Job snapshot is missing a required front, left, or right view; re-create this job"
            );
        }
        return List.copyOf(inputImages);
    }

    private static final class InvalidAiInputSnapshotException extends RuntimeException {
        private InvalidAiInputSnapshotException(String message) {
            super(message);
        }

        private InvalidAiInputSnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private void recordAcceptance(
            AiSubmitJobRequest request,
            AiJobResponse response
    ) {
        if (!request.jobId().equals(response.jobId())
                || !request.attemptId().equals(response.attemptId())) {
            throw new IllegalStateException(
                    "AI returned a different job attempt"
            );
        }

        transaction.executeWithoutResult(tx -> {
            ReconstructionJob job = jobRepository
                    .findByIdForUpdate(request.jobId())
                    .orElseThrow();

            // Không cập nhật nếu job đã bị hủy hoặc attempt đã thay đổi.
            if (job.getStatus() != ReconstructionJobStatus.queued
                    || !request.attemptId().equals(job.getAttemptId())) {
                return;
            }

            if ("queued".equals(response.status())) {
                return;
            }

            if ("running".equals(response.status())
                    || "needs_selection".equals(response.status())
                    || "needs_mask_review".equals(response.status())) {
                job.setStatus(ReconstructionJobStatus.running);
                job.setStartedAt(response.startedAt() != null ? response.startedAt() : Instant.now());
                job.setProgress(response.progress());

                if (response.stage() != null) {
                    try {
                        job.setStage(
                                ReconstructionStage.valueOf(response.stage())
                        );
                    } catch (IllegalArgumentException ex) {
                        job.setStage(ReconstructionStage.preprocessing);
                    }
                }

                // Thời điểm Spring nhận được trạng thái từ AI.
                job.setLastHeartbeatAt(Instant.now());

                log.info(
                        "AI started job {} with attempt {}",
                        job.getId(),
                        job.getAttemptId()
                );

            } else if ("failed".equals(response.status())) {
                job.setStatus(ReconstructionJobStatus.failed);
                job.setErrorCode(response.errorCode());
                job.setErrorMessage(response.errorMessage());
                job.setCompletedAt(response.completedAt());

                // Scan đã khóa input từ lúc submit.
                job.getScan().setStatus(
                        com.memento3d.scans.domain.model.ScanStatus.failed
                );

            } else if ("done".equals(response.status())) {
                /*
                 * AI done hiện chỉ có nghĩa GLB đã tạo local.
                 * Chưa được chuyển Spring sang done khi chưa lưu output.
                 */
                job.setStatus(ReconstructionJobStatus.running);
                job.setStartedAt(response.startedAt());
                job.setStage(ReconstructionStage.exporting);
                job.setProgress(null);

                log.info(
                        "AI generated local output for job {}; storage pending",
                        job.getId()
                );

            } else {
                throw new IllegalStateException(
                        "Unknown AI job status: " + response.status()
                );
            }

            jobRepository.saveAndFlush(job);
        });
    }
}
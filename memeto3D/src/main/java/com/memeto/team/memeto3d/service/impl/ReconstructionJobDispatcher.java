package com.memeto.team.memeto3d.service.impl;

import com.cloudinary.Cloudinary;
import com.memeto.team.memeto3d.dto.AiJobResponse;
import com.memeto.team.memeto3d.dto.AiSubmitJobRequest;
import com.memeto.team.memeto3d.entity.ReconstructionJob;
import com.memeto.team.memeto3d.entity.ReconstructionJobStatus;
import com.memeto.team.memeto3d.entity.ReconstructionStage;
import com.memeto.team.memeto3d.exception.AiClientException;
import com.memeto.team.memeto3d.repository.ReconstructionJobRepository;
import com.memeto.team.memeto3d.service.AiGenerationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.memeto.team.memeto3d.entity.ExecutionMode;

@Component
@Slf4j
public class ReconstructionJobDispatcher {

    private final ReconstructionJobRepository jobRepository;
    private final AiGenerationClient aiClient;
    private final Cloudinary cloudinary;
    private final TransactionTemplate transaction;
    private final boolean enabled;

    public ReconstructionJobDispatcher(
            ReconstructionJobRepository jobRepository,
            AiGenerationClient aiClient,
            Cloudinary cloudinary,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai.dispatch-enabled:false}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
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
            /*
             * Giữ queued và giữ nguyên attempt.
             * Lần sau gửi lại cùng request để AI chống nhận trùng.
             */
            log.warn(
                    "AI dispatch pending: jobId={}, upstreamStatus={}",
                    jobId,
                    ex.getUpstreamStatus()
            );
        } catch (Exception ex) {
            log.error("Failed to dispatch AI job {}", jobId, ex);
        }
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
                    != com.memeto.team.memeto3d.entity.ExecutionMode.real) {
                throw new IllegalStateException(
                        "This dispatcher only supports real execution mode"
                );
            }

            if (job.getScan().getDeletedAt() != null) {
                throw new IllegalStateException(
                        "Cannot dispatch a job belonging to a deleted scan"
                );
            }

            Object value = job.getInputSnapshot()
                    .get("cloudinary_public_id");

            if (!(value instanceof String publicId)
                    || publicId.isBlank()) {
                throw new IllegalStateException(
                        "Job input snapshot has no Cloudinary public ID"
                );
            }

            if (job.getAttemptId() == null) {
                job.setAttemptId(UUID.randomUUID());
            }

            /*
             * Ảnh hiện tại được upload dưới dạng image/public.
             * Không dùng storagePath như URL và không tự nối chuỗi URL.
             */
            String inputUrl = cloudinary.url()
                    .secure(true)
                    .resourceType("image")
                    .type("upload")
                    .generate(publicId);

            jobRepository.saveAndFlush(job);

            return new AiSubmitJobRequest(
                    job.getId(),
                    job.getAttemptId(),
                    job.getInputImageId(),
                    inputUrl,
                    job.getProfile()
            );
        });
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

            if ("running".equals(response.status())) {
                job.setStatus(ReconstructionJobStatus.running);
                job.setStartedAt(response.startedAt());
                job.setProgress(response.progress());

                if (response.stage() != null) {
                    job.setStage(
                            ReconstructionStage.valueOf(response.stage())
                    );
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
                        com.memeto.team.memeto3d.entity.ScanStatus.failed
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
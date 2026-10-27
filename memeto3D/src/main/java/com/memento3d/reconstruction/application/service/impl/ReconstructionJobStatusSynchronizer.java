package com.memento3d.reconstruction.application.service.impl;

import com.memento3d.scans.domain.model.Scan;

import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionStage;
import com.memento3d.scans.domain.model.ScanStatus;
import com.memento3d.reconstruction.domain.exception.AiClientException;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.application.service.ReconstructionJobFinalizer;
import com.memento3d.scans.application.service.ScanStatusCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.memento3d.reconstruction.domain.model.ExecutionMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ReconstructionJobStatusSynchronizer {

    private final ReconstructionJobRepository jobRepository;
    private final AiGenerationClient aiClient;
    private final ScanStatusCacheService cacheService;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final ReconstructionJobFinalizer finalizer;

    public ReconstructionJobStatusSynchronizer(
            ReconstructionJobRepository jobRepository,
            AiGenerationClient aiClient,
            ScanStatusCacheService cacheService,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai.sync-enabled:false}") boolean enabled,
            ReconstructionJobFinalizer finalizer
    ) {
        this.jobRepository = jobRepository;
        this.aiClient = aiClient;
        this.cacheService = cacheService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.finalizer = finalizer;

        log.info(
                "AI status synchronizer initialized: enabled={}",
                enabled
        );
    }

    @Scheduled(fixedDelayString = "${app.ai.sync-delay-ms:10000}")
    public void synchronizeJobs() {
        log.info("AI status sync tick: enabled={}", enabled);

        if (!enabled) {
            return;
        }

        List<ReconstructionJob> jobs =
                jobRepository.findByExecutionModeAndStatusOrderByQueuedAtAsc(
                        ExecutionMode.real,
                        ReconstructionJobStatus.running,
                        PageRequest.of(0, 20)
                );

        log.info("AI status sync found {} running jobs", jobs.size());

        for (ReconstructionJob job : jobs) {
            UUID jobId = job.getId();
            UUID attemptId = job.getAttemptId();

            if (attemptId == null) {
                log.warn("Running job {} has no attempt ID", jobId);
                continue;
            }

            try {
                // HTTP nằm ngoài transaction database.
                AiJobResponse response = aiClient.getJob(jobId);

                if (!attemptId.equals(response.attemptId())) {
                    log.warn(
                            "Ignoring mismatched AI attempt for job {}",
                            jobId
                    );
                    continue;
                }

                // MỚI: AI đã generate xong thì gọi finalizer.
                // Finalizer tải GLB, upload Cloudinary, lưu output
                // và chuyển job/scan sang done.
                if ("done".equals(response.status())) {
                    finalizer.finish(jobId, attemptId);
                    continue;
                }

                UUID failedScanId = applyStatus(
                        jobId,
                        attemptId,
                        response
                );

                // Transaction đã commit trước khi cập nhật Redis.
                if (failedScanId != null) {
                    try {
                        cacheService.cacheScanStatus(
                                failedScanId,
                                "FAILED"
                        );
                    } catch (Exception ex) {
                        log.warn(
                                "Failed to update cache for scan {}",
                                failedScanId,
                                ex
                        );
                    }
                }

            } catch (AiClientException ex) {
                // Không tự kết luận job thất bại khi mất kết nối AI.
                log.warn(
                        "AI status unavailable: jobId={}, upstreamStatus={}",
                        jobId,
                        ex.getUpstreamStatus()
                );
            } catch (Exception ex) {
                log.error("Failed to synchronize job {}", jobId, ex);
            }
        }
    }

    private UUID applyStatus(
            UUID jobId,
            UUID attemptId,
            AiJobResponse response
    ) {
        return transaction.execute(tx -> {
            ReconstructionJob job = jobRepository
                    .findByIdForUpdate(jobId)
                    .orElseThrow();

            if (job.getStatus() != ReconstructionJobStatus.running
                    || !attemptId.equals(job.getAttemptId())) {
                return null;
            }

            ReconstructionStage stage = null;
            if (response.stage() != null) {
                try {
                    stage = ReconstructionStage.valueOf(response.stage());
                } catch (IllegalArgumentException ex) {
                    stage = ReconstructionStage.preprocessing;
                }
            }

            Integer progress = response.progress();

            if (progress != null && (progress < 0 || progress > 100)) {
                throw new IllegalStateException(
                        "AI returned invalid progress"
                );
            }

            switch (response.status()) {
                case "running", "needs_selection", "needs_mask_review" -> {
                    job.setStage(stage != null ? stage : ReconstructionStage.preprocessing);
                    job.setProgress(progress != null ? progress : 15);
                    job.setLastHeartbeatAt(Instant.now());

                    if (job.getStartedAt() == null) {
                        job.setStartedAt(response.startedAt() != null ? response.startedAt() : Instant.now());
                    }
                }

                case "failed" -> {
                    job.setStatus(ReconstructionJobStatus.failed);
                    job.setStage(stage);
                    job.setProgress(progress);
                    job.setErrorCode(response.errorCode());
                    job.setErrorMessage(response.errorMessage());
                    job.setCompletedAt(
                            response.completedAt() != null
                                    ? response.completedAt()
                                    : Instant.now()
                    );
                    job.setLastHeartbeatAt(Instant.now());
                    job.getScan().setStatus(ScanStatus.failed);

                    jobRepository.saveAndFlush(job);

                    log.info(
                            "Job {} failed with code {}",
                            jobId,
                            response.errorCode()
                    );

                    return job.getScan().getId();
                }

                case "done" -> {
                    // Dự phòng; luồng chính đã gọi finalizer ở phía trên.
                    job.setStage(ReconstructionStage.exporting);
                    job.setProgress(null);
                    job.setLastHeartbeatAt(Instant.now());
                }

                case "queued" -> {
                    // Không hạ running trở lại queued.
                }

                default -> throw new IllegalStateException(
                        "Unknown AI status: " + response.status()
                );
            }

            jobRepository.saveAndFlush(job);
            return null;
        });
    }
}
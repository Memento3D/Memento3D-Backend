package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.entity.*;
import com.memeto.team.memeto3d.repository.ModelOutputRepository;
import com.memeto.team.memeto3d.repository.ReconstructionJobRepository;
import com.memeto.team.memeto3d.service.ScanStatusCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MockReconstructionJobRunner {

    private final ReconstructionJobRepository jobRepository;
    private final ModelOutputRepository outputRepository;
    private final ScanStatusCacheService cacheService;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final String mockOutputId;

    public MockReconstructionJobRunner(
            ReconstructionJobRepository jobRepository,
            ModelOutputRepository outputRepository,
            ScanStatusCacheService cacheService,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai.mock-enabled:false}") boolean enabled,
            @Value("${app.ai.mock-output-id:}") String mockOutputId
    ) {
        this.jobRepository = jobRepository;
        this.outputRepository = outputRepository;
        this.cacheService = cacheService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.mockOutputId = mockOutputId;

        log.info("Mock AI runner initialized: enabled={}", enabled);
    }

    @Scheduled(fixedDelayString = "${app.ai.mock-delay-ms:2000}")
    public void runMockJobs() {
        if (!enabled) {
            return;
        }

        try {
            UUID templateId = UUID.fromString(mockOutputId.trim());

            // Xử lý job đang chạy trước để tránh bị job mới chiếm lượt.
            processJobs(ReconstructionJobStatus.running, templateId);
            processJobs(ReconstructionJobStatus.queued, templateId);

        } catch (Exception ex) {
            log.error(
                    "Mock AI runner failed; check mock output configuration",
                    ex
            );
        }
    }

    private void processJobs(
            ReconstructionJobStatus status,
            UUID templateId
    ) {
        List<ReconstructionJob> jobs =
                jobRepository.findByExecutionModeAndStatusOrderByQueuedAtAsc(
                        ExecutionMode.mock,
                        status,
                        PageRequest.of(0, 20)
                );

        for (ReconstructionJob candidate : jobs) {
            try {
                CacheUpdate update = advanceJob(
                        candidate.getId(),
                        templateId
                );

                // Transaction đã commit trước khi cập nhật Redis.
                if (update != null) {
                    try {
                        cacheService.cacheScanStatus(
                                update.scanId(),
                                update.status()
                        );
                    } catch (Exception ex) {
                        log.warn(
                                "Mock job saved but cache update failed: scanId={}",
                                update.scanId(),
                                ex
                        );
                    }
                }

            } catch (Exception ex) {
                log.error(
                        "Failed to advance mock job {}",
                        candidate.getId(),
                        ex
                );
            }
        }
    }

    private CacheUpdate advanceJob(UUID jobId, UUID templateId) {
        return transaction.execute(tx -> {
            ReconstructionJob job = jobRepository
                    .findByIdForUpdate(jobId)
                    .orElseThrow();

            if (job.getExecutionMode() != ExecutionMode.mock
                    || (job.getStatus() != ReconstructionJobStatus.queued
                    && job.getStatus() != ReconstructionJobStatus.running)) {
                return null;
            }

            if (job.getScan().getDeletedAt() != null
                    || job.getScan().getStatus() == ScanStatus.deleted) {
                return null;
            }

            ModelOutput template = outputRepository.findById(templateId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Mock output template not found: " + templateId
                    ));

            if (!"glb".equals(template.getFormat())
                    || template.getJob().getExecutionMode() != ExecutionMode.real
                    || template.getJob().getStatus()
                    != ReconstructionJobStatus.done
                    || !template.getAttemptId().equals(
                    template.getJob().getAttemptId()
            )) {
                throw new IllegalStateException(
                        "Mock template must be a completed real GLB output"
                );
            }

            Instant now = Instant.now();

            if (job.getStatus() == ReconstructionJobStatus.queued) {
                job.setAttemptId(UUID.randomUUID());
                job.setStatus(ReconstructionJobStatus.running);
                job.setStartedAt(now);
                job.setCompletedAt(null);
                job.setStage(ReconstructionStage.downloading);
                job.setProgress(5);
                job.setLastHeartbeatAt(now);
                job.setWorkerId("spring-mock");
                job.setErrorCode(null);
                job.setErrorMessage(null);

                jobRepository.saveAndFlush(job);

                log.info(
                        "Mock job {} started with attempt {}",
                        jobId,
                        job.getAttemptId()
                );

                return new CacheUpdate(
                        job.getScan().getId(),
                        "PROCESSING"
                );
            }

            if (job.getStartedAt() == null || job.getAttemptId() == null) {
                throw new IllegalStateException(
                        "Running mock job has no startedAt or attemptId"
                );
            }

            long elapsedSeconds = Math.max(
                    0,
                    Duration.between(job.getStartedAt(), now).getSeconds()
            );

            job.setLastHeartbeatAt(now);

            if (elapsedSeconds >= 40) {
                saveOutput(job, template);

                job.setStatus(ReconstructionJobStatus.done);
                job.setStage(null);
                job.setProgress(100);
                job.setCompletedAt(now);
                job.setErrorCode(null);
                job.setErrorMessage(null);
                job.getScan().setStatus(ScanStatus.done);

                jobRepository.saveAndFlush(job);
                outputRepository.flush();

                log.info(
                        "Mock job {} completed using template output {}",
                        jobId,
                        templateId
                );

                return new CacheUpdate(
                        job.getScan().getId(),
                        "COMPLETED"
                );
            }

            ReconstructionStage nextStage;
            int nextProgress;

            if (elapsedSeconds < 5) {
                nextStage = ReconstructionStage.downloading;
                nextProgress = 5;
            } else if (elapsedSeconds < 10) {
                nextStage = ReconstructionStage.preprocessing;
                nextProgress = 15;
            } else if (elapsedSeconds < 20) {
                nextStage = ReconstructionStage.shape_generation;
                nextProgress = 45;
            } else if (elapsedSeconds < 30) {
                nextStage = ReconstructionStage.texturing;
                nextProgress = 75;
            } else {
                nextStage = ReconstructionStage.exporting;
                nextProgress = 95;
            }

            if (job.getStage() != nextStage) {
                log.info(
                        "Mock job {} stage={}, progress={}",
                        jobId,
                        nextStage,
                        nextProgress
                );
            }

            job.setStage(nextStage);
            job.setProgress(nextProgress);
            jobRepository.saveAndFlush(job);

            return null;
        });
    }

    private void saveOutput(
            ReconstructionJob job,
            ModelOutput template
    ) {
        ModelOutput existing = outputRepository
                .findByJob_IdAndAttemptIdAndFormat(
                        job.getId(),
                        job.getAttemptId(),
                        "glb"
                )
                .orElse(null);

        // Chống tạo output trùng khi scheduler chạy lại.
        if (existing != null) {
            if (!template.getSha256().equalsIgnoreCase(
                    existing.getSha256()
            )) {
                throw new IllegalStateException(
                        "Existing mock output has a different SHA-256"
                );
            }
            return;
        }

        outputRepository.save(
                ModelOutput.builder()
                        .job(job)
                        .attemptId(job.getAttemptId())
                        .format(template.getFormat())
                        .storageProvider(template.getStorageProvider())
                        .storagePublicId(template.getStoragePublicId())
                        .storageAssetId(template.getStorageAssetId())
                        .deliveryUrl(template.getDeliveryUrl())
                        .fileSizeBytes(template.getFileSizeBytes())
                        .sha256(template.getSha256())
                        .build()
        );
    }

    private record CacheUpdate(UUID scanId, String status) {
    }
}
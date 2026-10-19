package com.memento3d.admin.service.impl;

import com.memento3d.admin.service.AdminReconstructionService;
import com.memento3d.reconstruction.api.response.ReconstructionJobResponse;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.exception.BadRequestException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReconstructionServiceImpl implements AdminReconstructionService {

    private final ReconstructionJobRepository jobRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<ReconstructionJobResponse> listJobs(ReconstructionJobStatus status, ReconstructionTier tier, Pageable pageable) {
        return jobRepository.findAllWithFilters(status, tier, pageable)
                .map(this::mapJob);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconstructionJobResponse getJobDetail(UUID id) {
        ReconstructionJob job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tác vụ tái tạo 3D với mã: " + id));
        return mapJob(job);
    }

    @Override
    @Transactional
    public ReconstructionJobResponse retryFailedJob(UUID id, UUID adminActorId) {
        ReconstructionJob job = jobRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tác vụ tái tạo 3D với mã: " + id));

        // Business rule: Do not retry RUNNING or COMPLETED (done) jobs
        if (job.getStatus() == ReconstructionJobStatus.running) {
            throw new BadRequestException("Tác vụ đang trong tiến trình xử lý trên worker GPU, không thể thử lại.");
        }
        if (job.getStatus() == ReconstructionJobStatus.done) {
            throw new BadRequestException("Tác vụ tái tạo 3D đã hoàn thành thành công, không được thử lại.");
        }

        ReconstructionJobStatus oldStatus = job.getStatus();
        job.setStatus(ReconstructionJobStatus.queued);
        job.setRetryCount(job.getRetryCount() != null ? job.getRetryCount() + 1 : 1);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setQueuedAt(Instant.now());
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setProgress(0);

        ReconstructionJob saved = jobRepository.save(job);

        log.info("Admin {} retried reconstruction job {} (retry count: {})", adminActorId, id, saved.getRetryCount());
        auditLogService.log(adminActorId, "RECONSTRUCTION_RETRIED", "ReconstructionJob", id.toString(), oldStatus.name(), "queued (retry=" + saved.getRetryCount() + ")");

        return mapJob(saved);
    }

    private ReconstructionJobResponse mapJob(ReconstructionJob j) {
        return ReconstructionJobResponse.builder()
                .jobId(j.getId())
                .scanId(j.getScan() != null ? j.getScan().getId() : null)
                .inputImageId(j.getInputImageId())
                .tier(j.getTier())
                .orderId(j.getOrderId())
                .status(j.getStatus())
                .stage(j.getStage())
                .progress(j.getProgress())
                .profile(j.getProfile())
                .executionMode(j.getExecutionMode())
                .retryCount(j.getRetryCount())
                .errorCode(j.getErrorCode())
                .errorMessage(j.getErrorMessage())
                .queuedAt(j.getQueuedAt())
                .startedAt(j.getStartedAt())
                .completedAt(j.getCompletedAt())
                .build();
    }
}

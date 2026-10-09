package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memeto.team.memeto3d.entity.ExecutionMode;
import com.memeto.team.memeto3d.entity.ReconstructionJob;
import com.memeto.team.memeto3d.entity.ReconstructionJobStatus;
import com.memeto.team.memeto3d.entity.ReconstructionStage;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ReconstructionJobResponse {

    @JsonProperty("job_id")
    private UUID jobId;

    @JsonProperty("scan_id")
    private UUID scanId;

    @JsonProperty("input_image_id")
    private UUID inputImageId;

    private ReconstructionJobStatus status;

    private ReconstructionStage stage;

    private Integer progress;

    private String profile;

    @JsonProperty("execution_mode")
    private ExecutionMode executionMode;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("queued_at")
    private Instant queuedAt;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("completed_at")
    private Instant completedAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public static ReconstructionJobResponse fromEntity(
            ReconstructionJob job
    ) {
        return ReconstructionJobResponse.builder()
                .jobId(job.getId())
                .scanId(job.getScan().getId())
                .inputImageId(job.getInputImageId())
                .status(job.getStatus())
                .stage(job.getStage())
                .progress(job.getProgress())
                .profile(job.getProfile())
                .executionMode(job.getExecutionMode())
                .retryCount(job.getRetryCount())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .queuedAt(job.getQueuedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
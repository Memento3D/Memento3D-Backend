package com.memento3d.reconstruction.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memento3d.reconstruction.domain.model.ExecutionMode;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionStage;
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

    @JsonProperty("tier")
    private com.memento3d.reconstruction.domain.model.ReconstructionTier tier;

    @JsonProperty("order_id")
    private UUID orderId;

    private ReconstructionJobStatus status;

    private ReconstructionStage stage;

    private Integer progress;

    private String profile;

    @JsonProperty("output_kind") private String outputKind;
    @JsonProperty("customization_ready") private boolean customizationReady;

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
                .tier(job.getTier())
                .orderId(job.getOrderId())
                .status(job.getStatus())
                .stage(job.getStage())
                .progress(job.getProgress())
                .profile(job.getProfile())
                .outputKind(job.getOutputKind())
                .customizationReady(job.getStatus()==ReconstructionJobStatus.done && job.isCustomizationReady())
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
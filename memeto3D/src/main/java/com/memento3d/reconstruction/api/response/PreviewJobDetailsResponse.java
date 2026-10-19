package com.memento3d.reconstruction.api.response;

import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PreviewJobDetailsResponse {
    private UUID jobId;
    private UUID scanId;
    private ReconstructionTier tier;
    private ReconstructionJobStatus status;
    private Integer progress;
    private String previewViewerUrl;
    private boolean watermarked;
    private boolean canDownloadFullResolution;
    private Instant queuedAt;
    private Instant completedAt;

    public static PreviewJobDetailsResponse fromEntity(ReconstructionJob job, String previewUrl) {
        return PreviewJobDetailsResponse.builder()
                .jobId(job.getId())
                .scanId(job.getScan().getId())
                .tier(job.getTier())
                .status(job.getStatus())
                .progress(job.getProgress())
                .previewViewerUrl(previewUrl)
                .watermarked(job.getTier() == ReconstructionTier.PREVIEW)
                .canDownloadFullResolution(job.getTier() == ReconstructionTier.HIGH_QUALITY)
                .queuedAt(job.getQueuedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}

package com.memento3d.modelreview.api.response;

import com.memento3d.modelreview.domain.model.ModelVersion;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelVersionResponse {
    private UUID id;
    private UUID scanId;
    private UUID jobId;
    private Integer versionNumber;
    private String modelUrl;
    private String previewUrl;
    private String thumbnailUrl;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public static ModelVersionResponse fromEntity(ModelVersion version) {
        if (version == null) return null;
        return ModelVersionResponse.builder()
                .id(version.getId())
                .scanId(version.getScan() != null ? version.getScan().getId() : null)
                .jobId(version.getJob() != null ? version.getJob().getId() : null)
                .versionNumber(version.getVersionNumber())
                .modelUrl(version.getModelUrl())
                .previewUrl(version.getModelUrl())
                .thumbnailUrl(version.getModelUrl())
                .status(version.getStatus())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .build();
    }
}

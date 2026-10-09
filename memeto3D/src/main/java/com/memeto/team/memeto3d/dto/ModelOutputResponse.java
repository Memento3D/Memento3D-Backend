package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memeto.team.memeto3d.entity.ModelOutput;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ModelOutputResponse {

    @JsonProperty("output_id")
    private UUID outputId;

    @JsonProperty("job_id")
    private UUID jobId;

    @JsonProperty("attempt_id")
    private UUID attemptId;

    private String format;

    @JsonProperty("delivery_url")
    private String deliveryUrl;

    @JsonProperty("file_size_bytes")
    private Long fileSizeBytes;

    private String sha256;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static ModelOutputResponse fromEntity(ModelOutput output) {
        return ModelOutputResponse.builder()
                .outputId(output.getId())
                .jobId(output.getJob().getId())
                .attemptId(output.getAttemptId())
                .format(output.getFormat())
                .deliveryUrl(output.getDeliveryUrl())
                .fileSizeBytes(output.getFileSizeBytes())
                .sha256(output.getSha256())
                .createdAt(output.getCreatedAt())
                .build();
    }
}
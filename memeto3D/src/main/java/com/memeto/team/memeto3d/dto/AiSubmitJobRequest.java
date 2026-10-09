package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AiSubmitJobRequest(
        @JsonProperty("job_id") UUID jobId,
        @JsonProperty("attempt_id") UUID attemptId,
        @JsonProperty("input_image_id") UUID inputImageId,
        @JsonProperty("input_url") String inputUrl,
        String profile
) {
}
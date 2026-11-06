package com.memento3d.reconstruction.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record AiSubmitJobRequest(
        @JsonProperty("job_id") UUID jobId,
        @JsonProperty("attempt_id") UUID attemptId,
        @JsonProperty("input_images") List<InputImage> inputImages,
        String profile
) {
    public record InputImage(
            @JsonProperty("image_id") UUID imageId,
            @JsonProperty("input_url") String inputUrl,
            @JsonProperty("view_hint") String viewHint
    ) {
    }
}
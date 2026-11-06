package com.memento3d.reconstruction.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReconstructionJobRequest {

    @JsonProperty("input_image_id")
    @Schema(
            description = "Deprecated optional compatibility field. If omitted, the front-labeled image is selected automatically.",
            deprecated = true
    )
    private UUID inputImageId;

    @Builder.Default
    private String profile = "default";
}
package com.memento3d.reconstruction.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @NotNull(message = "input_image_id is required")
    @JsonProperty("input_image_id")
    private UUID inputImageId;

    @Builder.Default
    private String profile = "default";
}
package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateReconstructionJobRequest {

    @NotNull(message = "input_image_id is required")
    @JsonProperty("input_image_id")
    private UUID inputImageId;

    // Hiện tại chỉ hỗ trợ profile default.
    // Backend sẽ ánh xạ profile sang cấu hình AI thực tế.
    @Pattern(
            regexp = "default",
            message = "Only profile 'default' is currently supported"
    )
    private String profile = "default";
}
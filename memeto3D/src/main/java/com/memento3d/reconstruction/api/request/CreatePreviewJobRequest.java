package com.memento3d.reconstruction.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePreviewJobRequest {

    @NotNull(message = "scan_id is required")
    private UUID scanId;

    @NotNull(message = "input_image_id is required")
    private UUID inputImageId;

    private String profile;
}

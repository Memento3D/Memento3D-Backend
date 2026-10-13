package com.memento3d.scans.api.request;

import com.memento3d.scans.domain.model.InputMode;
import com.memento3d.scans.domain.model.Scan;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body to create a new scan session")
public class CreateScanRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Schema(description = "Scan session name (required)", example = "My 3D Object Scan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Optional scan description", example = "Scan of a clay sculpture under diffuse lighting")
    private String description;

    @Schema(description = "Optional notes for the scan", example = "Captured 45 photos with turntable")
    private String notes;

    @JsonProperty("input_mode")
    @JsonAlias({"inputMode"})
    @Pattern(regexp = "^(image|video)$", message = "Input mode must be 'image' or 'video'")
    @Schema(description = "Capture mode: 'image' or 'video'", example = "image", defaultValue = "image")
    @Builder.Default
    private String inputMode = "image";

    public String getInputMode() {
        if (inputMode == null || inputMode.isBlank()) {
            return "image";
        }
        return inputMode;
    }
}

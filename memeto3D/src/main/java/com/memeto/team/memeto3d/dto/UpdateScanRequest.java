package com.memeto.team.memeto3d.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body to update scan session metadata")
public class UpdateScanRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Schema(description = "Scan session name (optional)", example = "Updated 3D Object Scan")
    private String name;

    @Schema(description = "Optional scan description", example = "Updated scan description")
    private String description;

    @Schema(description = "Optional notes for the scan", example = "Updated notes")
    private String notes;
}

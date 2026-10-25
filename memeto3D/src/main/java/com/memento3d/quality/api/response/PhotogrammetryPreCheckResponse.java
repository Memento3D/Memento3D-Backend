package com.memento3d.quality.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Automated AI Photogrammetry Pre-Check Assessment")
public class PhotogrammetryPreCheckResponse {

    @Schema(description = "Scan session UUID")
    private UUID scanId;

    @Schema(description = "Overall image quality score (0 - 100)", example = "87")
    private Integer qualityScore;

    @Schema(description = "Multi-angle photogrammetry coverage score (0 - 100)", example = "81")
    private Integer coverageScore;

    @Schema(description = "Blurriness penalty index (0 = crisp, 100 = completely blurry)", example = "5")
    private Integer blurScore;

    @Schema(description = "Total number of images evaluated", example = "45")
    private Integer totalImages;

    @Schema(description = "List of duplicate or redundant image filenames")
    private List<String> duplicateImages;

    @Schema(description = "Gaps in 360-degree capture angle", example = "[\"back-left\", \"bottom\"]")
    private List<String> missingAngles;

    @Schema(description = "Actionable user remediation instructions")
    private List<String> suggestions;

    @Schema(description = "Whether the photo dataset satisfies 3D reconstruction requirements", example = "true")
    private boolean readyForReconstruction;

    @Schema(description = "AI Confidence Score (0.0 to 1.0)", example = "0.92")
    private Double confidenceScore;

    @Schema(description = "Automated decision outcome", example = "AUTO_APPROVED")
    private String decision; // AUTO_APPROVED, MANUAL_REVIEW, REJECT_NEEDS_MORE_PHOTOS
}

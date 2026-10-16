package com.memento3d.scans.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload to update editable metadata of a scan image")
public class UpdateScanImageRequest {

    @JsonProperty("sort_order")
    @Schema(description = "Sequence order of the image in the capture set", example = "5")
    private Integer sortOrder;

    @JsonProperty("is_excluded")
    @Schema(description = "Whether to exclude this image from 3D reconstruction", example = "true")
    private Boolean isExcluded;

    @JsonProperty("exclude_reason")
    @Size(max = 100, message = "Exclude reason must not exceed 100 characters")
    @Schema(description = "Reason why image is excluded (e.g. blurry, poor lighting)", example = "Blurry foreground")
    private String excludeReason;
}

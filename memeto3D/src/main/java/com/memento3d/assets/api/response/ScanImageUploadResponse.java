package com.memento3d.assets.api.response;

import com.memento3d.scans.api.response.ScanImageResponse;
import com.memento3d.scans.domain.model.Scan;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/** Response returned after POST /api/v1/scans/{id}/images. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Schema(description = "Result of a supplemental image upload")
public class ScanImageUploadResponse {

    @Schema(description = "Newly created image record")
    private ScanImageResponse image;

    @JsonProperty("scan_file_count")
    @Schema(description = "Updated total image count of the scan", example = "46")
    private Integer scanFileCount;

    @JsonProperty("scan_total_size_bytes")
    @Schema(description = "Updated total size of all images in the scan (bytes)", example = "94371840")
    private Long scanTotalSizeBytes;
}
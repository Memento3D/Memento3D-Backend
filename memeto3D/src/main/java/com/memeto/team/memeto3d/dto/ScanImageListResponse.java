package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/** Paginated image list — returned by GET /api/v1/scans/{id}/images (UC-03). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Schema(description = "Paginated list of images in a scan session")
public class ScanImageListResponse {

    @JsonProperty("images")
    @Schema(description = "Images sorted by sort_order ASC, created_at ASC")
    private List<ScanImageResponse> images;

    @JsonProperty("total_count")
    @Schema(example = "100")
    private Long totalCount;

    @Schema(example = "1")
    private Integer page;

    @Schema(example = "20")
    private Integer limit;

    @JsonProperty("total_pages")
    @Schema(example = "5")
    private Integer totalPages;
}
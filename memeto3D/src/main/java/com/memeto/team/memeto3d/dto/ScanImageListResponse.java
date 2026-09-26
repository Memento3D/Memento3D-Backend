package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * Paginated response for GET /api/v1/scans/{id}/images.
 * Powers the Review Gallery (UC-03) by returning all images in capture order.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated list of images belonging to a scan session")
public class ScanImageListResponse {

    @JsonProperty("images")
    @Schema(description = "Images in the current page, sorted by sort_order ASC then created_at ASC")
    private List<ScanImageResponse> images;

    @JsonProperty("total_count")
    @Schema(description = "Total number of images in the scan (all upload_status included)", example = "100")
    private Long totalCount;

    @Schema(description = "Current page number (1-based)", example = "1")
    private Integer page;

    @Schema(description = "Number of items per page", example = "20")
    private Integer limit;

    @JsonProperty("total_pages")
    @Schema(description = "Total number of pages", example = "5")
    private Integer totalPages;
}

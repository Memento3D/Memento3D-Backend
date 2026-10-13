package com.memento3d.scans.api.response;

import com.memento3d.scans.domain.model.Scan;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated scan list response")
public class ScanListResponse {

    @JsonProperty("scans")
    @Schema(description = "List of scan sessions in the current page")
    private List<ScanResponse> scans;

    @JsonProperty("total_count")
    @Schema(description = "Total number of matching scan sessions", example = "42")
    private Long totalCount;

    @Schema(description = "Current page number (1-based)", example = "1")
    private Integer page;

    @Schema(description = "Number of items per page", example = "20")
    private Integer limit;

    @JsonProperty("total_pages")
    @Schema(description = "Total number of pages", example = "3")
    private Integer totalPages;

    @JsonProperty("items")
    public List<ScanResponse> getItems() {
        return scans;
    }

    @JsonProperty("items")
    public void setItems(List<ScanResponse> items) {
        if (this.scans == null) {
            this.scans = items;
        }
    }
}

package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ScanVideoMetadataResponse {
    @JsonProperty("duration_seconds")
    private BigDecimal durationSeconds;

    private String resolution;

    private BigDecimal fps;

    @JsonProperty("upload_status")
    private String uploadStatus;

    @JsonProperty("storage_path")
    private String storagePath;

    @JsonProperty("preview_url")
    private String previewUrl;
}

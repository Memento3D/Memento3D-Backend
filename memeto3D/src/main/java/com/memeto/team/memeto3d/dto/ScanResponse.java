package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memeto.team.memeto3d.entity.Scan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Scan session details response")
public class ScanResponse {

    @Schema(description = "Unique scan session identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @JsonProperty("user_id")
    @Schema(description = "User ID who owns this scan session", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID userId;

    @Schema(description = "Scan name", example = "My 3D Object Scan")
    private String name;

    @Schema(description = "Scan description", example = "Scan of a clay sculpture")
    private String description;

    @Schema(description = "Additional notes", example = "Turntable 360")
    private String notes;

    @JsonProperty("input_mode")
    @Schema(description = "Capture mode ('image' or 'video')", example = "image")
    private String inputMode;

    @Schema(description = "Current status of the scan session", example = "draft")
    private String status;

    @JsonProperty("file_count")
    @Schema(description = "Total number of images/videos in scan", example = "0")
    private Integer fileCount;

    @JsonProperty("total_size_bytes")
    @Schema(description = "Total size of media files in bytes", example = "0")
    private Long totalSizeBytes;

    @JsonProperty("thumbnail_path")
    @Schema(description = "Relative path or URL to preview thumbnail", example = "/storage/thumbnails/scan_1.jpg")
    private String thumbnailPath;

    @JsonProperty("device_info")
    @Schema(description = "Capture device metadata", example = "{\"model\": \"iPhone 15 Pro\", \"os\": \"iOS 17.2\"}")
    private Map<String, Object> deviceInfo;

    @JsonProperty("duplicated_from")
    @Schema(description = "Source scan ID if cloned", example = "null")
    private UUID duplicatedFrom;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the scan was created")
    private Instant createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Timestamp when the scan was last updated")
    private Instant updatedAt;

    @JsonProperty("deleted_at")
    @Schema(description = "Timestamp when the scan was soft-deleted, if applicable")
    private Instant deletedAt;

    public static ScanResponse fromEntity(Scan scan) {
        if (scan == null) {
            return null;
        }
        return ScanResponse.builder()
                .id(scan.getId())
                .userId(scan.getUser() != null ? scan.getUser().getId() : null)
                .name(scan.getName())
                .description(scan.getDescription())
                .notes(scan.getNotes())
                .inputMode(scan.getInputMode() != null ? scan.getInputMode().name() : null)
                .status(scan.getStatus() != null ? scan.getStatus().name() : null)
                .fileCount(scan.getFileCount())
                .totalSizeBytes(scan.getTotalSizeBytes())
                .thumbnailPath(scan.getThumbnailPath())
                .deviceInfo(scan.getDeviceInfo())
                .duplicatedFrom(scan.getDuplicatedFrom())
                .createdAt(scan.getCreatedAt())
                .updatedAt(scan.getUpdatedAt())
                .deletedAt(scan.getDeletedAt())
                .build();
    }
}

package com.memento3d.scans.api.response;

import com.memento3d.scans.domain.model.Scan;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memento3d.scans.domain.model.ScanImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single scan image (Review Gallery UC-03 and supplemental upload UC-04). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Schema(description = "Single scan image")
public class ScanImageResponse {

    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @JsonProperty("scan_id")
    @Schema(example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID scanId;

    @JsonProperty("original_filename")
    @Schema(example = "IMG_0042.jpg")
    private String originalFilename;

    @JsonProperty("thumbnail_path")
    @Schema(example = "/storage/thumbs/img_0042.jpg")
    private String thumbnailPath;

    @JsonProperty("file_size_bytes")
    @Schema(example = "2048576")
    private Long fileSizeBytes;

    @JsonProperty("mime_type")
    @Schema(example = "image/jpeg")
    private String mimeType;

    @Schema(example = "4032")
    private Integer width;

    @Schema(example = "3024")
    private Integer height;

    @Schema(example = "jpg")
    private String format;

    @JsonProperty("sort_order")
    @Schema(example = "0")
    private Integer sortOrder;

    @JsonProperty("view_hint")
    @Schema(description = "Capture angle metadata used for multi-view reconstruction",
            allowableValues = {"front", "left", "right", "back"})
    private String viewHint;

    @JsonProperty("upload_status")
    @Schema(example = "uploaded", allowableValues = {"pending", "uploaded", "failed"})
    private String uploadStatus;

    @JsonProperty("is_excluded")
    @Schema(example = "false")
    private Boolean isExcluded;

    @JsonProperty("exclude_reason")
    private String excludeReason;

    @JsonProperty("needs_quality_check")
    @Schema(description = "True when image was added after a QC run and awaits incremental check",
            example = "true")
    private Boolean needsQualityCheck;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static ScanImageResponse fromEntity(ScanImage image) {
        if (image == null) return null;
        return ScanImageResponse.builder()
                .id(image.getId())
                .scanId(image.getScan() != null ? image.getScan().getId() : null)
                .originalFilename(image.getOriginalFilename())
                .thumbnailPath(image.getThumbnailPath())
                .fileSizeBytes(image.getFileSizeBytes())
                .mimeType(image.getMimeType())
                .width(image.getWidth())
                .height(image.getHeight())
                .format(image.getFormat())
                .sortOrder(image.getSortOrder())
                .viewHint(image.getViewHint())
                .uploadStatus(image.getUploadStatus() != null ? image.getUploadStatus().name() : null)
                .isExcluded(image.getIsExcluded())
                .excludeReason(image.getExcludeReason())
                .needsQualityCheck(image.getNeedsQualityCheck())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memeto.team.memeto3d.entity.ScanImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single image within a scan session.
 * Returned by GET /api/v1/scans/{id}/images to power the Review Gallery (UC-03).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Single scan image details for the Review Gallery")
public class ScanImageResponse {

    @Schema(description = "Unique image identifier", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @JsonProperty("scan_id")
    @Schema(description = "Parent scan session ID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID scanId;

    @JsonProperty("original_filename")
    @Schema(description = "Original file name uploaded by the user", example = "IMG_0042.jpg")
    private String originalFilename;

    @JsonProperty("thumbnail_path")
    @Schema(description = "Relative path or URL to the image thumbnail for gallery display",
            example = "/storage/thumbs/img_0042.jpg")
    private String thumbnailPath;

    @JsonProperty("file_size_bytes")
    @Schema(description = "Size of the image file in bytes", example = "2048576")
    private Long fileSizeBytes;

    @JsonProperty("mime_type")
    @Schema(description = "MIME type of the image", example = "image/jpeg")
    private String mimeType;

    @Schema(description = "Image width in pixels", example = "4032")
    private Integer width;

    @Schema(description = "Image height in pixels", example = "3024")
    private Integer height;

    @Schema(description = "Image file format", example = "jpg")
    private String format;

    @JsonProperty("sort_order")
    @Schema(description = "Explicit capture sequence order (FR-017)", example = "0")
    private Integer sortOrder;

    @JsonProperty("upload_status")
    @Schema(description = "Upload status of this image: pending, uploaded, or failed. " +
            "Failed images are always included so FE can show them in the grid.",
            example = "uploaded", allowableValues = {"pending", "uploaded", "failed"})
    private String uploadStatus;

    @JsonProperty("is_excluded")
    @Schema(description = "Whether this image was excluded from quality check (FR-055)", example = "false")
    private Boolean isExcluded;

    @JsonProperty("exclude_reason")
    @Schema(description = "Reason for exclusion, if any", example = "motion_blur")
    private String excludeReason;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the image record was created")
    private Instant createdAt;

    public static ScanImageResponse fromEntity(ScanImage image) {
        if (image == null) {
            return null;
        }
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
                .uploadStatus(image.getUploadStatus() != null ? image.getUploadStatus().name() : null)
                .isExcluded(image.getIsExcluded())
                .excludeReason(image.getExcludeReason())
                .createdAt(image.getCreatedAt())
                .build();
    }
}

package com.memento3d.quality.api.response;

import com.memento3d.scans.api.response.DuplicateImageInfo;
import com.memento3d.scans.domain.model.Scan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Detailed technical quality check inspection results for a specific image in a quality run")
public class ImageQualityDetailResponse {

    @JsonProperty("id")
    @Schema(description = "ID of the image quality result record", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID id;

    @JsonProperty("run_id")
    @Schema(description = "ID of the quality check run", example = "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e")
    private UUID runId;

    @JsonProperty("image_id")
    @Schema(description = "ID of the scan image", example = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f")
    private UUID imageId;

    @JsonProperty("scan_id")
    @Schema(description = "ID of the parent scan session", example = "d4e5f6a7-b89c-0d1e-2f3a-4b5c6d7e8f9a")
    private UUID scanId;

    @JsonProperty("original_filename")
    @Schema(description = "Original uploaded filename", example = "IMG_0042.JPG")
    private String originalFilename;

    @JsonProperty("storage_path")
    @Schema(description = "Internal/private storage path", example = "uploads/scans/d4e5f6a7/IMG_0042.JPG")
    private String storagePath;

    @JsonProperty("image_url")
    @Schema(description = "URL for full resolution image preview / zoom", example = "https://res.cloudinary.com/demo/image/upload/v1234/IMG_0042.jpg")
    private String imageUrl;

    @JsonProperty("thumbnail_path")
    @Schema(description = "Thumbnail image path", example = "https://res.cloudinary.com/demo/image/upload/v1234/thumb_IMG_0042.jpg")
    private String thumbnailPath;

    @JsonProperty("overall_status")
    @Schema(description = "Overall image quality status ('pass', 'warn', 'fail')", example = "warn")
    private String overallStatus;

    // --- Sharpness / Blur ---
    @JsonProperty("blur_score")
    @Schema(description = "Sharpness / Blur score of the image", example = "128.45")
    private BigDecimal blurScore;

    @JsonProperty("is_blurry")
    @Schema(description = "Whether the image is classified as blurry", example = "false")
    private Boolean isBlurry;

    @JsonProperty("blur_type")
    @Schema(description = "Type of blur detected ('motion_blur', 'defocus_blur', or null)", example = "motion_blur")
    private String blurType;

    // --- Brightness / Exposure ---
    @JsonProperty("brightness_score")
    @Schema(description = "Mean brightness value (0-255)", example = "142.30")
    private BigDecimal brightnessScore;

    @JsonProperty("exposure_status")
    @Schema(description = "Categorized exposure status ('NORMAL', 'TOO_DARK', 'TOO_BRIGHT', 'EXPOSURE_WARNING')", example = "NORMAL")
    private String exposureStatus;

    @JsonProperty("is_too_dark")
    @Schema(description = "Whether the image is under-exposed / too dark", example = "false")
    private Boolean isTooDark;

    @JsonProperty("is_too_bright")
    @Schema(description = "Whether the image is over-exposed / too bright", example = "false")
    private Boolean isTooBright;

    @JsonProperty("saturated_pixel_pct")
    @Schema(description = "Percentage of saturated (blown-out or black) pixels", example = "1.25")
    private BigDecimal saturatedPixelPct;

    @JsonProperty("has_exposure_warning")
    @Schema(description = "Whether exposure warning was triggered", example = "false")
    private Boolean hasExposureWarning;

    // --- Features & Overlap ---
    @JsonProperty("feature_count")
    @Schema(description = "Count of keypoint feature points detected in the image", example = "4520")
    private Integer featureCount;

    @JsonProperty("is_low_features")
    @Schema(description = "Whether feature count is below minimum threshold for 3D reconstruction", example = "false")
    private Boolean isLowFeatures;

    @JsonProperty("overlap_score")
    @Schema(description = "Primary overlap percentage score", example = "78.50")
    private BigDecimal overlapScore;

    @JsonProperty("overlap_score_prev")
    @Schema(description = "Overlap percentage with previous image in sequence", example = "80.00")
    private BigDecimal overlapScorePrev;

    @JsonProperty("overlap_score_next")
    @Schema(description = "Overlap percentage with next image in sequence", example = "77.00")
    private BigDecimal overlapScoreNext;

    @JsonProperty("has_insufficient_overlap")
    @Schema(description = "Whether overlap with neighbor images is below requirement", example = "false")
    private Boolean hasInsufficientOverlap;

    // --- Duplicates ---
    @JsonProperty("is_duplicate")
    @Schema(description = "Whether the image is an exact or near duplicate of another image", example = "true")
    private Boolean isDuplicate;

    @JsonProperty("is_exact_duplicate")
    @Schema(description = "Whether the image is an exact hash duplicate", example = "true")
    private Boolean isExactDuplicate;

    @JsonProperty("is_near_duplicate")
    @Schema(description = "Whether the image is a near perceptual duplicate", example = "false")
    private Boolean isNearDuplicate;

    @JsonProperty("duplicate_group_id")
    @Schema(description = "ID of duplicate cluster group if image is in a duplicate group")
    private UUID duplicateGroupId;

    @JsonProperty("duplicate_of")
    @Schema(description = "Details of the primary image that this image is duplicate of")
    private DuplicateImageInfo duplicateOf;

    // --- Additional Metrics for Histogram / Technical Specs ---
    @JsonProperty("snr_score")
    @Schema(description = "Signal-to-Noise ratio score (dB)", example = "35.60")
    private BigDecimal snrScore;

    @JsonProperty("is_high_noise")
    @Schema(description = "Whether image contains high noise levels", example = "false")
    private Boolean isHighNoise;

    @JsonProperty("watermark_area_pct")
    @Schema(description = "Percentage of area covered by watermark/logo", example = "0.00")
    private BigDecimal watermarkAreaPct;

    @JsonProperty("has_watermark_warning")
    @Schema(description = "Whether watermark detection warning was triggered", example = "false")
    private Boolean hasWatermarkWarning;

    @JsonProperty("aspect_ratio_warning")
    @Schema(description = "Whether image aspect ratio differs from standard set", example = "false")
    private Boolean aspectRatioWarning;

    // --- Failure Reasons & Remediation Suggestions ---
    @JsonProperty("failure_reasons")
    @Schema(description = "List of structured failure/warning reason details with FE suggestions")
    private List<FailureReasonDetail> failureReasons;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when quality inspection was recorded")
    private Instant createdAt;
}

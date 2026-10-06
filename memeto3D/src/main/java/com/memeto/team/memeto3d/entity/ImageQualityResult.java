package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Technical inspection results for a single scan image within a quality check run.
 * Maps to the image_quality_results table.
 */
@Entity
@Table(name = "image_quality_results", indexes = {
        @Index(name = "idx_iqr_run", columnList = "quality_check_run_id"),
        @Index(name = "idx_iqr_image", columnList = "scan_image_id"),
        @Index(name = "idx_iqr_status", columnList = "overall_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageQualityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quality_check_run_id", nullable = false)
    private QualityCheckRun qualityCheckRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_image_id", nullable = false)
    private ScanImage scanImage;

    // FR-041 sharpness / blur
    @Column(name = "sharpness_score", precision = 10, scale = 2)
    private BigDecimal sharpnessScore;

    @Column(name = "is_blurry", nullable = false)
    @Builder.Default
    private Boolean isBlurry = false;

    @Column(name = "blur_type", length = 20)
    private String blurType;

    // FR-043, FR-044 brightness / exposure
    @Column(name = "mean_brightness", precision = 6, scale = 2)
    private BigDecimal meanBrightness;

    @Column(name = "is_too_dark", nullable = false)
    @Builder.Default
    private Boolean isTooDark = false;

    @Column(name = "is_too_bright", nullable = false)
    @Builder.Default
    private Boolean isTooBright = false;

    @Column(name = "saturated_pixel_pct", precision = 5, scale = 2)
    private BigDecimal saturatedPixelPct;

    @Column(name = "has_exposure_warning", nullable = false)
    @Builder.Default
    private Boolean hasExposureWarning = false;

    // FR-047 features
    @Column(name = "feature_count")
    private Integer featureCount;

    @Column(name = "is_low_features", nullable = false)
    @Builder.Default
    private Boolean isLowFeatures = false;

    // FR-048 overlap
    @Column(name = "overlap_score_prev", precision = 5, scale = 2)
    private BigDecimal overlapScorePrev;

    @Column(name = "overlap_score_next", precision = 5, scale = 2)
    private BigDecimal overlapScoreNext;

    @Column(name = "has_insufficient_overlap", nullable = false)
    @Builder.Default
    private Boolean hasInsufficientOverlap = false;

    // FR-045, FR-046 duplicates
    @Column(name = "is_exact_duplicate", nullable = false)
    @Builder.Default
    private Boolean isExactDuplicate = false;

    @Column(name = "is_near_duplicate", nullable = false)
    @Builder.Default
    private Boolean isNearDuplicate = false;

    @Column(name = "duplicate_group_id")
    private UUID duplicateGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_image_id")
    private ScanImage duplicateOfImage;

    // FR-050 aspect ratio
    @Column(name = "aspect_ratio_warning", nullable = false)
    @Builder.Default
    private Boolean aspectRatioWarning = false;

    // FR-075 watermark
    @Column(name = "has_watermark_warning", nullable = false)
    @Builder.Default
    private Boolean hasWatermarkWarning = false;

    @Column(name = "watermark_area_pct", precision = 5, scale = 2)
    private BigDecimal watermarkAreaPct;

    // FR-076 noise
    @Column(name = "snr_score", precision = 8, scale = 2)
    private BigDecimal snrScore;

    @Column(name = "is_high_noise", nullable = false)
    @Builder.Default
    private Boolean isHighNoise = false;

    @Column(name = "overall_status", nullable = false, length = 10)
    @Builder.Default
    private String overallStatus = "pass";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "failure_reasons")
    private List<String> failureReasons;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

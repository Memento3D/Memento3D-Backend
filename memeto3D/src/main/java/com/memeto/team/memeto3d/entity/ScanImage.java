package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Single image within a scan session. Maps to the scan_images table.
 *
 * DB migration required:
 *   ALTER TABLE scan_images
 *     ADD COLUMN upload_status VARCHAR(20) NOT NULL DEFAULT 'uploaded'
 *       CHECK (upload_status IN ('pending','uploaded','failed')),
 *     ADD COLUMN thumbnail_path VARCHAR(500),
 *     ADD COLUMN needs_quality_check BOOLEAN NOT NULL DEFAULT FALSE;
 */
@Entity
@Table(name = "scan_images", indexes = {
        @Index(name = "idx_scan_images_scan",     columnList = "scan_id"),
        @Index(name = "idx_scan_images_excluded", columnList = "scan_id, is_excluded"),
        @Index(name = "idx_scan_images_qc_flag",  columnList = "scan_id, needs_quality_check")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScanImage {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** Internal private storage path (FR-166). Not exposed to FE. */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** Public-facing thumbnail for gallery grid display. */
    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    /** Explicit capture-order sequence (FR-017). */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exif_data")
    private Map<String, Object> exifData;

    @Column(name = "hash_md5", length = 32)
    private String hashMd5;

    @Column(name = "hash_perceptual", length = 64)
    private String hashPerceptual;

    @Column(name = "is_excluded", nullable = false)
    @Builder.Default
    private Boolean isExcluded = false;

    @Column(name = "exclude_reason", length = 100)
    private String excludeReason;

    @Column(name = "cloudinary_asset_id", length = 255)
    private String cloudinaryAssetId;

    @Column(name = "cloudinary_public_id", length = 500)
    private String cloudinaryPublicId;

    /** Upload status. Failed images are still returned to FE (UC-03). */
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    @Builder.Default
    private ScanImageUploadStatus uploadStatus = ScanImageUploadStatus.uploaded;

    /**
     * Flag set to TRUE when this image was added after a quality_check_run exists.
     * Lets the QC engine process only new images without re-checking the entire set.
     * Reset to FALSE after the incremental QC run picks it up.
     */
    @Column(name = "needs_quality_check", nullable = false)
    @Builder.Default
    private Boolean needsQualityCheck = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
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
 * Represents a single image uploaded within a scan session.
 * Maps to the scan_images table.
 *
 * Note: upload_status is an application-level column added to support
 * the Review Gallery (UC-03) requirement that failed images are visible to FE.
 * It must be added to the DB schema via migration:
 *   ALTER TABLE scan_images
 *     ADD COLUMN upload_status VARCHAR(20) NOT NULL DEFAULT 'uploaded'
 *     CHECK (upload_status IN ('pending', 'uploaded', 'failed'));
 */
@Entity
@Table(name = "scan_images", indexes = {
        @Index(name = "idx_scan_images_scan", columnList = "scan_id"),
        @Index(name = "idx_scan_images_excluded", columnList = "scan_id, is_excluded")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /**
     * Internal storage path (private, not served directly to FE).
     * FR-166: private storage.
     */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /**
     * Public-facing thumbnail path served to FE for the gallery grid.
     * May be null if thumbnail has not been generated yet.
     */
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

    /**
     * Explicit ordering within the scan (FR-017).
     * Fallback sort is created_at ASC when sort_order values are equal.
     */
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

    /**
     * Upload status of this image record.
     * Supports Review Gallery requirement: failed images are returned with
     * their status so FE can display them in the grid (not hidden).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    @Builder.Default
    private ScanImageUploadStatus uploadStatus = ScanImageUploadStatus.uploaded;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}

package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scans", indexes = {
        @Index(name = "idx_scans_user", columnList = "user_id"),
        @Index(name = "idx_scans_status", columnList = "status"),
        @Index(name = "idx_scans_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_scans_user_status_date", columnList = "user_id, status, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode", nullable = false, length = 10)
    @Builder.Default
    private InputMode inputMode = InputMode.image;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ScanStatus status = ScanStatus.draft;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "file_count", nullable = false)
    @Builder.Default
    private Integer fileCount = 0;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "total_size_bytes", nullable = false)
    @Builder.Default
    private Long totalSizeBytes = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_info")
    private Map<String, Object> deviceInfo;

    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath;

    @Column(name = "duplicated_from")
    private UUID duplicatedFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

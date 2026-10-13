package com.memento3d.reconstruction.domain.model;

import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanImage;

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
@Table(
        name = "reconstruction_jobs",
        indexes = {
                @Index(
                        name = "idx_reconstruction_jobs_scan_created",
                        columnList = "scan_id, created_at"
                ),
                @Index(
                        name = "idx_reconstruction_jobs_status_queued",
                        columnList = "status, queued_at"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconstructionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false, updatable = false)
    private Scan scan;

    /*
     * Snapshot ID, deliberately not a foreign key in this first version.
     * ScanImage currently supports hard deletion.
     * Service must validate that this image belongs to the scan.
     */
    @Column(name = "input_image_id", nullable = false, updatable = false)
    private UUID inputImageId;

    /*
     * Stable input metadata: publicId, assetId, hash when available.
     * Do not put temporary signed URLs or secrets here.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false,
            columnDefinition = "jsonb")
    private Map<String, Object> inputSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReconstructionJobStatus status =
            ReconstructionJobStatus.queued;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 30)
    private ReconstructionStage stage;

    @Column(name = "profile", nullable = false, length = 100,
            updatable = false)
    private String profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 10,
            updatable = false)
    @Builder.Default
    private ExecutionMode executionMode = ExecutionMode.real;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "toolchain_config", nullable = false,
            columnDefinition = "jsonb")
    private Map<String, Object> toolchainConfig;

    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "progress")
    private Integer progress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (queuedAt == null) {
            queuedAt = Instant.now();
        }
    }
}
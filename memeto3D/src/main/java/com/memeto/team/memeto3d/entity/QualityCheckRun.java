package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quality_check_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityCheckRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @Column(name = "run_number", nullable = false)
    @Builder.Default
    private Integer runNumber = 1;

    @Column(name = "algo_version", nullable = false)
    private String algoVersion;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "running";

    @Column(name = "total_images", nullable = false)
    @Builder.Default
    private Integer totalImages = 0;

    @Column(name = "passed_images", nullable = false)
    @Builder.Default
    private Integer passedImages = 0;

    @Column(name = "failed_images", nullable = false)
    @Builder.Default
    private Integer failedImages = 0;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "score_category", length = 10)
    private String scoreCategory;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}

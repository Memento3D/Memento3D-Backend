package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @Column(name = "run_number", nullable = false)
    private Integer runNumber;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // 'running', 'completed', 'failed'

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "score_category", length = 50)
    private String scoreCategory; // 'green', 'amber', 'red'

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "coverage_analysis", columnDefinition = "TEXT")
    private String coverageAnalysis;

    @Column(name = "difficulty_score")
    private Integer difficultyScore;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "qualityCheckRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImageQualityResult> imageQualityResults = new ArrayList<>();
}

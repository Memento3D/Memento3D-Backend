package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "image_quality_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageQualityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quality_check_run_id", nullable = false)
    private QualityCheckRun qualityCheckRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_image_id", nullable = false)
    private ScanImage scanImage;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // 'pass', 'warn', 'fail'

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

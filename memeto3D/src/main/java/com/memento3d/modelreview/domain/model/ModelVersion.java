package com.memento3d.modelreview.domain.model;

import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.scans.domain.model.Scan;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "model_versions",
        indexes = {
                @Index(name = "idx_model_versions_scan", columnList = "scan_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_model_versions_scan_num", columnNames = {"scan_id", "version_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private Scan scan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private ReconstructionJob job;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "model_url", nullable = false, columnDefinition = "TEXT")
    private String modelUrl;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "MODEL_READY"; // MODEL_READY, AWAITING_APPROVAL, APPROVED, REVISION_REQUESTED, LOCKED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isLocked() {
        return "LOCKED".equalsIgnoreCase(this.status);
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(this.status) || "LOCKED".equalsIgnoreCase(this.status);
    }
}

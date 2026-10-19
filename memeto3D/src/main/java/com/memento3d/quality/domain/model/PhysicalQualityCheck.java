package com.memento3d.quality.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "physical_quality_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhysicalQualityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "print_job_id", nullable = false)
    private UUID printJobId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "inspector_id", nullable = false)
    private UUID inspectorId;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // PASS, FAIL, PENDING_REVIEW

    @Column(name = "model_accuracy_pass", nullable = false)
    private boolean modelAccuracyPass;

    @Column(name = "surface_quality_pass", nullable = false)
    private boolean surfaceQualityPass;

    @Column(name = "dimensions_pass", nullable = false)
    private boolean dimensionsPass;

    @Column(name = "color_finishing_pass", nullable = false)
    private boolean colorFinishingPass;

    @Column(name = "structural_integrity_pass", nullable = false)
    private boolean structuralIntegrityPass;

    @Column(name = "packaging_pass", nullable = false)
    private boolean packagingPass;

    @Column(name = "failure_reason_code", length = 100)
    private String failureReasonCode;

    @Column(name = "evidence_urls", columnDefinition = "TEXT")
    private String evidenceUrls;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reprint_required", nullable = false)
    private boolean reprintRequired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

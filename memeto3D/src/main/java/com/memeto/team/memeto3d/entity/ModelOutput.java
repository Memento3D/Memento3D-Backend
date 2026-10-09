package com.memeto.team.memeto3d.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "model_outputs",
        indexes = {
                @Index(
                        name = "idx_model_outputs_job",
                        columnList = "job_id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_model_outputs_job_attempt_format",
                        columnNames = {"job_id", "attempt_id", "format"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private ReconstructionJob job;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(name = "storage_provider", nullable = false, length = 30)
    private String storageProvider;

    @Column(name = "storage_public_id", nullable = false, length = 500)
    private String storagePublicId;

    @Column(name = "storage_asset_id", length = 255)
    private String storageAssetId;

    @Column(name = "delivery_url", nullable = false, columnDefinition = "TEXT")
    private String deliveryUrl;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
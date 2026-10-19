package com.memento3d.assets.domain.model;

import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "digital_assets",
        indexes = {
                @Index(name = "idx_digital_assets_user", columnList = "user_id"),
                @Index(name = "idx_digital_assets_job", columnList = "job_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigitalAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private ReconstructionJob job;

    @Column(name = "file_format", nullable = false, length = 10)
    private String fileFormat;

    @Column(name = "storage_provider", nullable = false, length = 30)
    @Builder.Default
    private String storageProvider = "CLOUDINARY";

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "delivery_url", nullable = false, columnDefinition = "TEXT")
    private String deliveryUrl;

    @Column(name = "file_size_bytes", nullable = false)
    @Builder.Default
    private Long fileSizeBytes = 0L;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "READY";

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.memento3d.modelreview.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "model_reviews",
        indexes = {
                @Index(name = "idx_model_reviews_version", columnList = "model_version_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersion modelVersion;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String decision; // APPROVED, REVISION_REQUESTED

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "revision_reason", length = 100)
    private String revisionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

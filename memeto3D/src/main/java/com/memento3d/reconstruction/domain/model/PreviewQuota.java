package com.memento3d.reconstruction.domain.model;

import com.memento3d.identity.domain.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "preview_quotas",
        indexes = {
                @Index(name = "idx_preview_quotas_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "total_quota", nullable = false)
    @Builder.Default
    private Integer totalQuota = 3;

    @Column(name = "used_quota", nullable = false)
    @Builder.Default
    private Integer usedQuota = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean hasAvailableQuota() {
        return usedQuota < totalQuota;
    }

    public void incrementUsed() {
        if (!hasAvailableQuota()) {
            throw new IllegalStateException("Preview quota exceeded");
        }
        this.usedQuota++;
    }

    public int getRemainingQuota() {
        return Math.max(0, totalQuota - usedQuota);
    }
}

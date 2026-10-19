package com.memento3d.entitlement.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "entitlements",
        indexes = {
                @Index(name = "idx_entitlements_owner", columnList = "owner_id"),
                @Index(name = "idx_entitlements_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_entitlements_owner_resource_type",
                        columnNames = {"owner_id", "resource_id", "entitlement_type"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_type", nullable = false, length = 50)
    private EntitlementType entitlementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private EntitlementStatus status = EntitlementStatus.ACTIVE;

    @Column(name = "valid_until")
    private Instant validUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isValid() {
        if (status != EntitlementStatus.ACTIVE) {
            return false;
        }
        return validUntil == null || validUntil.isAfter(Instant.now());
    }
}

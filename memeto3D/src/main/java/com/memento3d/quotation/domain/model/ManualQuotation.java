package com.memento3d.quotation.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "manual_quotations",
        indexes = {
                @Index(name = "idx_manual_quotations_user", columnList = "user_id"),
                @Index(name = "idx_manual_quotations_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualQuotation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assigned_manager_id")
    private UUID assignedManagerId;

    @Column(name = "scan_id")
    private UUID scanId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "SUBMITTED"; // DRAFT, SUBMITTED, UNDER_REVIEW, QUOTED, ACCEPTED, REJECTED, EXPIRED

    @Column(name = "customer_notes", columnDefinition = "TEXT")
    private String customerNotes;

    @Column(name = "manager_notes", columnDefinition = "TEXT")
    private String managerNotes;

    @Column(name = "customization_spec", columnDefinition = "TEXT")
    private String customizationSpec;

    @Column(name = "quoted_amount", precision = 12, scale = 2)
    private BigDecimal quotedAmount;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.memento3d.inventory.domain.model;

import com.memento3d.material.domain.model.Material;
import com.memento3d.warehouse.domain.model.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_transactions",
        indexes = {
                @Index(name = "idx_transactions_wh_mat_created", columnList = "warehouse_id, material_id, created_at"),
                @Index(name = "idx_transactions_job", columnList = "print_job_id"),
                @Index(name = "idx_transactions_type", columnList = "transaction_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private InventoryTransactionType transactionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "delta_on_hand_gram", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal deltaOnHandGram = BigDecimal.ZERO;

    @Column(name = "delta_reserved_gram", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal deltaReservedGram = BigDecimal.ZERO;

    @Column(name = "before_on_hand_gram", nullable = false, precision = 14, scale = 2)
    private BigDecimal beforeOnHandGram;

    @Column(name = "after_on_hand_gram", nullable = false, precision = 14, scale = 2)
    private BigDecimal afterOnHandGram;

    @Column(name = "before_reserved_gram", nullable = false, precision = 14, scale = 2)
    private BigDecimal beforeReservedGram;

    @Column(name = "after_reserved_gram", nullable = false, precision = 14, scale = 2)
    private BigDecimal afterReservedGram;

    @Column(name = "print_job_id")
    private UUID printJobId;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

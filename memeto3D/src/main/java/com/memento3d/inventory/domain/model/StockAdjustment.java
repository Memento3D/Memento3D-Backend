package com.memento3d.inventory.domain.model;

import com.memento3d.material.domain.model.Material;
import com.memento3d.warehouse.domain.model.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "stock_adjustments",
        indexes = {
                @Index(name = "idx_adjustments_wh", columnList = "warehouse_id"),
                @Index(name = "idx_adjustments_mat", columnList = "material_id"),
                @Index(name = "idx_adjustments_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "adjustment_code", nullable = false, unique = true, length = 100)
    private String adjustmentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "current_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualQuantity;

    @Column(name = "difference", nullable = false, precision = 14, scale = 2)
    private BigDecimal difference;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private StockAdjustmentStatus status = StockAdjustmentStatus.DRAFT;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

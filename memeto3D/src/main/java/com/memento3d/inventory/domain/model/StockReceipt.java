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
        name = "stock_receipts",
        indexes = {
                @Index(name = "idx_stock_receipts_wh_mat", columnList = "warehouse_id, material_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 100)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity_gram", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityGram;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "supplier", length = 200)
    private String supplier;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "received_by")
    private UUID receivedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

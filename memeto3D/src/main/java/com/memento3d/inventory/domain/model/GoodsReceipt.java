package com.memento3d.inventory.domain.model;

import com.memento3d.warehouse.domain.model.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "goods_receipts",
        indexes = {
                @Index(name = "idx_goods_receipts_wh", columnList = "warehouse_id"),
                @Index(name = "idx_goods_receipts_status", columnList = "status"),
                @Index(name = "idx_goods_receipts_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "receipt_code", nullable = false, unique = true, length = 100)
    private String receiptCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "supplier", length = 200)
    private String supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private GoodsReceiptStatus status = GoodsReceiptStatus.DRAFT;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GoodsReceiptItem> items = new ArrayList<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    public void addItem(GoodsReceiptItem item) {
        items.add(item);
        item.setReceipt(this);
    }
}

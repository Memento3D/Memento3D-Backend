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
        name = "warehouse_transfers",
        indexes = {
                @Index(name = "idx_transfers_src_wh", columnList = "source_warehouse_id"),
                @Index(name = "idx_transfers_dst_wh", columnList = "destination_warehouse_id"),
                @Index(name = "idx_transfers_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transfer_code", nullable = false, unique = true, length = 100)
    private String transferCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private WarehouseTransferStatus status = WarehouseTransferStatus.DRAFT;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<WarehouseTransferItem> items = new ArrayList<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

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

    public void addItem(WarehouseTransferItem item) {
        items.add(item);
        item.setTransfer(this);
    }
}

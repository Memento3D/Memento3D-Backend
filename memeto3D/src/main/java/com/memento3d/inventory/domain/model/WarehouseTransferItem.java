package com.memento3d.inventory.domain.model;

import com.memento3d.material.domain.model.Material;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "warehouse_transfer_items",
        indexes = {
                @Index(name = "idx_transfer_items_transfer", columnList = "transfer_id"),
                @Index(name = "idx_transfer_items_material", columnList = "material_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private WarehouseTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}

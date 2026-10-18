package com.memento3d.inventory.domain.model;

import com.memento3d.material.domain.model.Material;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "goods_receipt_items",
        indexes = {
                @Index(name = "idx_goods_receipt_items_receipt", columnList = "receipt_id"),
                @Index(name = "idx_goods_receipt_items_material", columnList = "material_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private GoodsReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}

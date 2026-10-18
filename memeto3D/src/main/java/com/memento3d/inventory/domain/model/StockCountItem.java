package com.memento3d.inventory.domain.model;

import com.memento3d.material.domain.model.Material;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "stock_count_items",
        indexes = {
                @Index(name = "idx_stock_count_items_count", columnList = "stock_count_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_id", nullable = false)
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "system_quantity", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal systemQuantity = BigDecimal.ZERO;

    @Column(name = "counted_quantity", precision = 14, scale = 2)
    private BigDecimal countedQuantity;

    @Column(name = "variance", precision = 14, scale = 2)
    private BigDecimal variance;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}

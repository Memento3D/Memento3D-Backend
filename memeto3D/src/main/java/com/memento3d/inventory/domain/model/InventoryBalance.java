package com.memento3d.inventory.domain.model;

import com.memento3d.material.domain.model.Material;
import com.memento3d.warehouse.domain.model.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_balances",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_inventory_warehouse_material", columnNames = {"warehouse_id", "material_id"})
        },
        indexes = {
                @Index(name = "idx_inventory_warehouse", columnList = "warehouse_id"),
                @Index(name = "idx_inventory_material", columnList = "material_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "on_hand_gram", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal onHandGram = BigDecimal.ZERO;

    @Column(name = "reserved_gram", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal reservedGram = BigDecimal.ZERO;

    @Column(name = "minimum_stock", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal minimumStock = new BigDecimal("500.00");

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BigDecimal getAvailableGram() {
        BigDecimal onHand = onHandGram != null ? onHandGram : BigDecimal.ZERO;
        BigDecimal reserved = reservedGram != null ? reservedGram : BigDecimal.ZERO;
        return onHand.subtract(reserved);
    }

    public boolean isLowStock() {
        BigDecimal min = minimumStock != null ? minimumStock : (material != null && material.getMinimumStock() != null ? material.getMinimumStock() : BigDecimal.ZERO);
        return getAvailableGram().compareTo(min) <= 0 && getAvailableGram().compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isOutOfStock() {
        return getAvailableGram().compareTo(BigDecimal.ZERO) <= 0;
    }
}

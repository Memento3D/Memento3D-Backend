package com.memento3d.material.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "materials",
        indexes = {
                @Index(name = "idx_materials_status", columnList = "status"),
                @Index(name = "idx_materials_type", columnList = "material_type"),
                @Index(name = "idx_materials_category", columnList = "category"),
                @Index(name = "idx_materials_brand", columnList = "brand")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "unit", nullable = false, length = 50)
    @Builder.Default
    private String unit = "g";

    @Column(name = "brand", length = 100)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 50)
    private MaterialType materialType;

    @Column(name = "color", nullable = false, length = 50)
    private String color;

    @Column(name = "color_hex", length = 10)
    private String colorHex;

    @Column(name = "density", precision = 6, scale = 3)
    private BigDecimal density;

    @Column(name = "diameter", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal diameter = new BigDecimal("1.75");

    @Column(name = "print_temp_min")
    private Integer printTempMin;

    @Column(name = "print_temp_max")
    private Integer printTempMax;

    @Column(name = "bed_temp_min")
    private Integer bedTempMin;

    @Column(name = "bed_temp_max")
    private Integer bedTempMax;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "minimum_stock", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal minimumStock = new BigDecimal("500.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MaterialStatus status = MaterialStatus.ACTIVE;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

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

package com.memento3d.pricing.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "pricing_rules",
        indexes = {
                @Index(name = "idx_pricing_rules_active", columnList = "active"),
                @Index(name = "idx_pricing_rules_product", columnList = "product_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "calculation_type", nullable = false, length = 50)
    private String calculationType; // FIXED_PRICE, PERCENTAGE, QUANTITY_BASED, MANUAL_QUOTE

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "condition_type", length = 50)
    private String conditionType; // SIZE, COMPLEXITY, MATERIAL, FINISHING, PACKAGING

    @Column(name = "condition_value", length = 100)
    private String conditionValue;

    @Column(name = "modifier_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal modifierAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.memento3d.inventory.domain.model;

import com.memento3d.material.domain.model.Material;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "enterprise_stock_issue_items",
        indexes = {
                @Index(name = "idx_issue_items_issue", columnList = "issue_id"),
                @Index(name = "idx_issue_items_material", columnList = "material_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnterpriseStockIssueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private EnterpriseStockIssue issue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "requested_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal requestedQuantity;

    @Column(name = "issued_quantity", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal issuedQuantity = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}

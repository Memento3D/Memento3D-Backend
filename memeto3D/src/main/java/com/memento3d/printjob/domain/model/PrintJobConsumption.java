package com.memento3d.printjob.domain.model;

import com.memento3d.material.domain.model.Material;
import com.memento3d.warehouse.domain.model.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "print_job_consumptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_consumptions_job", columnNames = {"print_job_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintJobConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "print_job_id", nullable = false, unique = true)
    private PrintJob printJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "issued_gram", nullable = false, precision = 14, scale = 2)
    private BigDecimal issuedGram;

    @Column(name = "used_gram", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal usedGram = BigDecimal.ZERO;

    @Column(name = "waste_gram", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal wasteGram = BigDecimal.ZERO;

    @Column(name = "returned_gram", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal returnedGram = BigDecimal.ZERO;

    @Column(name = "reconciled", nullable = false)
    @Builder.Default
    private boolean reconciled = false;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciled_by")
    private UUID reconciledBy;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

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

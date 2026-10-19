package com.memento3d.printjob.domain.model;

import com.memento3d.material.domain.model.Material;
import com.memento3d.printers.domain.model.Printer;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "print_jobs",
        indexes = {
                @Index(name = "idx_print_jobs_status", columnList = "status"),
                @Index(name = "idx_print_jobs_printer", columnList = "printer_id"),
                @Index(name = "idx_print_jobs_material", columnList = "material_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_number", nullable = false, unique = true, length = 100)
    private String jobNumber;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "model_version_id")
    private UUID modelVersionId;

    @Column(name = "is_outsource", nullable = false)
    @Builder.Default
    private boolean isOutsource = false;

    @Column(name = "partner_name", length = 255)
    private String partnerName;

    @Column(name = "reprint_of_job_id")
    private UUID reprintOfJobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private Printer printer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PrintJobStatus status = PrintJobStatus.CREATED;

    @Column(name = "estimated_gram", nullable = false, precision = 14, scale = 2)
    private BigDecimal estimatedGram;

    @Column(name = "estimated_duration_seconds")
    private Integer estimatedDurationSeconds;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_by")
    private UUID createdBy;

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

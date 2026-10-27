package com.memento3d.printjob.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "resin_production_details",
        indexes = {
                @Index(name = "idx_resin_details_job", columnList = "print_job_id"),
                @Index(name = "idx_resin_details_order", columnList = "order_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResinProductionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "print_job_id", nullable = false, unique = true)
    private UUID printJobId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "model_version_id")
    private UUID modelVersionId;

    @Column(name = "stl_asset_id")
    private UUID stlAssetId;

    @Column(name = "stl_delivery_url", columnDefinition = "TEXT")
    private String stlDeliveryUrl;

    // Kích thước chuẩn bị theo mm
    @Column(name = "dim_x_mm", precision = 8, scale = 2)
    private BigDecimal dimXMm;

    @Column(name = "dim_y_mm", precision = 8, scale = 2)
    private BigDecimal dimYMm;

    @Column(name = "dim_z_mm", precision = 8, scale = 2)
    private BigDecimal dimZMm;

    // Kiểm tra kỹ thuật trước khi slice
    @Column(name = "is_mesh_watertight")
    private Boolean isMeshWatertight;

    @Column(name = "is_hollowed")
    private Boolean isHollowed;

    @Column(name = "has_drain_holes")
    private Boolean hasDrainHoles;

    @Column(name = "support_density", length = 50)
    private String supportDensity;

    // Thông tin CHITUBOX Slicer
    @Column(name = "slicer_name", length = 100)
    @Builder.Default
    private String slicerName = "CHITUBOX";

    @Column(name = "slicer_version", length = 50)
    private String slicerVersion;

    @Column(name = "printer_profile", length = 100)
    @Builder.Default
    private String printerProfile = "ELEGOO Saturn 4 Ultra 16K";

    @Column(name = "slice_file_format", length = 20)
    private String sliceFileFormat; // "GOO" hoặc "CTB"

    @Column(name = "slice_file_url", columnDefinition = "TEXT")
    private String sliceFileUrl;

    @Column(name = "slice_file_sha256", length = 64)
    private String sliceFileSha256;

    @Column(name = "preview_image_url", columnDefinition = "TEXT")
    private String previewImageUrl;

    // Resin profile
    @Column(name = "resin_type", length = 100)
    private String resinType;

    @Column(name = "resin_color", length = 50)
    private String resinColor;

    @Column(name = "is_custom_painted")
    @Builder.Default
    private Boolean isCustomPainted = false;

    @Column(name = "estimated_resin_ml", precision = 10, scale = 2)
    private BigDecimal estimatedResinMl;

    @Column(name = "estimated_print_time_seconds")
    private Integer estimatedPrintTimeSeconds;

    // Bàn giao tới máy
    @Enumerated(EnumType.STRING)
    @Column(name = "handoff_method", length = 50)
    private ResinHandoffMethod handoffMethod;

    @Column(name = "handoff_confirmed_by")
    private UUID handoffConfirmedBy;

    @Column(name = "handoff_confirmed_at")
    private Instant handoffConfirmedAt;

    @Column(name = "operator_confirmed_printing_at")
    private Instant operatorConfirmedPrintingAt;

    // Hậu xử lý (Post-Processing Checklist)
    @Column(name = "wash_completed")
    @Builder.Default
    private Boolean washCompleted = false;

    @Column(name = "support_removal_completed")
    @Builder.Default
    private Boolean supportRemovalCompleted = false;

    @Column(name = "uv_cure_completed")
    @Builder.Default
    private Boolean uvCureCompleted = false;

    @Column(name = "uv_cure_duration_minutes")
    private Integer uvCureDurationMinutes;

    @Column(name = "surface_finishing_completed")
    @Builder.Default
    private Boolean surfaceFinishingCompleted = false;

    // QC & Retry
    @Column(name = "qc_status", length = 30)
    private String qcStatus;

    @Column(name = "qc_notes", columnDefinition = "TEXT")
    private String qcNotes;

    @Column(name = "attempt_number")
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "failure_photo_urls", columnDefinition = "TEXT")
    private String failurePhotoUrls;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

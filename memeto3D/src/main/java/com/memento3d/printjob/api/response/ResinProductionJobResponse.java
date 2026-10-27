package com.memento3d.printjob.api.response;

import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.domain.model.ResinHandoffMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ResinProductionJobResponse {

    private UUID printJobId;
    private String jobNumber;
    private UUID orderId;
    private UUID orderItemId;
    private UUID modelVersionId;

    private PrintJobStatus status;
    private String statusDescription;

    // Files
    private String glbViewerUrl;
    private String stlGeometryUrl;
    private String sliceFileUrl;
    private String sliceFileFormat; // "GOO" hoặc "CTB"
    private String previewImageUrl;

    // Geometry & Check
    private BigDecimal dimXMm;
    private BigDecimal dimYMm;
    private BigDecimal dimZMm;
    private Boolean isMeshWatertight;
    private Boolean isHollowed;
    private Boolean hasDrainHoles;
    private String supportDensity;

    // Slicer & Machine Profile
    private String slicerName;
    private String slicerVersion;
    private String targetPrinterProfile; // ELEGOO Saturn 4 Ultra 16K
    private String resinType;
    private String resinColor;
    private Boolean isCustomPainted;
    private BigDecimal estimatedResinMl;
    private Integer estimatedPrintTimeSeconds;

    // Handoff & Printing
    private ResinHandoffMethod handoffMethod;
    private Instant handoffConfirmedAt;
    private Instant printingStartedAt;

    // Post-processing checklist
    private Boolean washCompleted;
    private Boolean supportRemovalCompleted;
    private Boolean uvCureCompleted;
    private Integer uvCureDurationMinutes;
    private Boolean surfaceFinishingCompleted;

    // QC & Reprints
    private String qcStatus;
    private String qcNotes;
    private Integer attemptNumber;
    private UUID reprintOfJobId;

    private Instant createdAt;
    private Instant updatedAt;
}

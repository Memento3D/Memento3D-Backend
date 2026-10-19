package com.memento3d.quality.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.quality.application.PhysicalQualityService;
import com.memento3d.quality.domain.model.PhysicalQualityCheck;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/print-jobs")
@RequiredArgsConstructor
@Tag(name = "Physical Quality Control", description = "Physical print quality inspection and reprint workflow")
public class PhysicalQualityController {

    private final PhysicalQualityService physicalQualityService;

    @Getter
    @Setter
    public static class RecordQcRequest {
        @NotNull(message = "Model accuracy check is required")
        private Boolean modelAccuracyPass;

        @NotNull(message = "Surface quality check is required")
        private Boolean surfaceQualityPass;

        @NotNull(message = "Dimensions check is required")
        private Boolean dimensionsPass;

        @NotNull(message = "Color & finishing check is required")
        private Boolean colorFinishingPass;

        @NotNull(message = "Structural integrity check is required")
        private Boolean structuralIntegrityPass;

        @NotNull(message = "Packaging check is required")
        private Boolean packagingPass;

        private String failureReasonCode;
        private String evidenceUrls;
        private String notes;
        private boolean requestReprint;
    }

    @Getter
    @Setter
    @Builder
    public static class PhysicalQcResponse {
        private UUID id;
        private UUID printJobId;
        private UUID orderItemId;
        private UUID inspectorId;
        private String status;
        private boolean modelAccuracyPass;
        private boolean surfaceQualityPass;
        private boolean dimensionsPass;
        private boolean colorFinishingPass;
        private boolean structuralIntegrityPass;
        private boolean packagingPass;
        private String failureReasonCode;
        private String evidenceUrls;
        private String notes;
        private boolean reprintRequired;
        private Instant createdAt;

        public static PhysicalQcResponse from(PhysicalQualityCheck qc) {
            return PhysicalQcResponse.builder()
                    .id(qc.getId())
                    .printJobId(qc.getPrintJobId())
                    .orderItemId(qc.getOrderItemId())
                    .inspectorId(qc.getInspectorId())
                    .status(qc.getStatus())
                    .modelAccuracyPass(qc.isModelAccuracyPass())
                    .surfaceQualityPass(qc.isSurfaceQualityPass())
                    .dimensionsPass(qc.isDimensionsPass())
                    .colorFinishingPass(qc.isColorFinishingPass())
                    .structuralIntegrityPass(qc.isStructuralIntegrityPass())
                    .packagingPass(qc.isPackagingPass())
                    .failureReasonCode(qc.getFailureReasonCode())
                    .evidenceUrls(qc.getEvidenceUrls())
                    .notes(qc.getNotes())
                    .reprintRequired(qc.isReprintRequired())
                    .createdAt(qc.getCreatedAt())
                    .build();
        }
    }

    @PostMapping("/{jobId}/qc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Record QC checklist result for a print job")
    public ResponseEntity<PhysicalQcResponse> recordQc(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID jobId,
            @Valid @RequestBody RecordQcRequest request) {

        PhysicalQualityCheck check = physicalQualityService.recordQcCheck(
                jobId,
                currentUser.getId(),
                request.getModelAccuracyPass(),
                request.getSurfaceQualityPass(),
                request.getDimensionsPass(),
                request.getColorFinishingPass(),
                request.getStructuralIntegrityPass(),
                request.getPackagingPass(),
                request.getFailureReasonCode(),
                request.getEvidenceUrls(),
                request.getNotes(),
                request.isRequestReprint()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(PhysicalQcResponse.from(check));
    }

    @GetMapping("/{jobId}/qc-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Get QC inspection history for a print job")
    public ResponseEntity<List<PhysicalQcResponse>> getHistory(@PathVariable UUID jobId) {
        List<PhysicalQcResponse> list = physicalQualityService.getQcHistory(jobId)
                .stream().map(PhysicalQcResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{jobId}/qc-status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check whether a print job has passed physical QC")
    public ResponseEntity<Map<String, Object>> getQcStatus(@PathVariable UUID jobId) {
        boolean passed = physicalQualityService.isPrintJobPassedQc(jobId);
        return ResponseEntity.ok(Map.of("printJobId", jobId, "passedQc", passed));
    }
}

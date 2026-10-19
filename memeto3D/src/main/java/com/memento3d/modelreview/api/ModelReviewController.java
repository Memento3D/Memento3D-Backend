package com.memento3d.modelreview.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.modelreview.api.response.ModelVersionResponse;
import com.memento3d.modelreview.application.service.ModelReviewService;
import com.memento3d.modelreview.domain.model.ModelVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Model Review & Approvals", description = "3D model customer inspection, revision requests, and production approval")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("isAuthenticated()")
public class ModelReviewController {

    private final ModelReviewService modelReviewService;

    @GetMapping("/api/v1/models/{scanId}/versions")
    @Operation(summary = "List model versions for a scan", description = "Retrieves version history and approval statuses")
    public ResponseEntity<List<ModelVersionResponse>> getVersions(
            @PathVariable UUID scanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ModelVersion> versions = modelReviewService.getVersionsForScan(scanId, userDetails.getId(), userDetails.getRole());
        return ResponseEntity.ok(versions.stream().map(ModelVersionResponse::fromEntity).toList());
    }

    @PostMapping("/api/v1/models/versions/{versionId}/approve")
    @Operation(summary = "Approve 3D model version for physical manufacturing")
    public ResponseEntity<ModelVersionResponse> approveVersion(
            @PathVariable UUID versionId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String notes = body != null ? body.get("notes") : null;
        ModelVersion version = modelReviewService.approveVersion(versionId, notes, userDetails.getId(), userDetails.getRole());
        return ResponseEntity.ok(ModelVersionResponse.fromEntity(version));
    }

    @PostMapping("/api/v1/models/versions/{versionId}/request-revision")
    @Operation(summary = "Request adjustment/revision on 3D model")
    public ResponseEntity<ModelVersionResponse> requestRevision(
            @PathVariable UUID versionId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String reason = body.get("reason");
        String notes = body.get("notes");
        ModelVersion version = modelReviewService.requestRevision(versionId, reason, notes, userDetails.getId(), userDetails.getRole());
        return ResponseEntity.ok(ModelVersionResponse.fromEntity(version));
    }
}

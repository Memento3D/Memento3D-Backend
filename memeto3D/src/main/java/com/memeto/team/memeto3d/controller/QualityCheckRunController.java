package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.ErrorResponse;
import com.memeto.team.memeto3d.dto.QualityCheckRunDetailResponse;
import com.memeto.team.memeto3d.dto.QualityCheckRunSummaryResponse;
import com.memeto.team.memeto3d.security.CustomUserDetails;
import com.memeto.team.memeto3d.service.QualityCheckRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Quality Check", description = "Quality Check endpoints")
public class QualityCheckRunController {

    private final QualityCheckRunService qualityCheckRunService;

    @GetMapping("/{id}/quality-runs")
    @Operation(
            summary = "Get all quality check runs for a scan",
            description = "Returns history of quality check runs for a scan, sorted by run_number DESC.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quality check runs retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan not found or access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<QualityCheckRunSummaryResponse>> getQualityCheckRuns(
            @PathVariable("id") UUID scanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("Get quality check runs for scan ID: {} by user: {}", scanId, userDetails.getId());
        List<QualityCheckRunSummaryResponse> response = qualityCheckRunService.getQualityCheckRuns(scanId, userDetails.getId(), userDetails.getRole());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/quality-runs/{run_id}")
    @Operation(
            summary = "Get detail of a specific quality check run",
            description = "Returns full details of a run, including image quality results.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Run details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan or Run not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<QualityCheckRunDetailResponse> getQualityCheckRun(
            @PathVariable("id") UUID scanId,
            @PathVariable("run_id") UUID runId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("Get quality check run ID: {} for scan ID: {} by user: {}", runId, scanId, userDetails.getId());
        QualityCheckRunDetailResponse response = qualityCheckRunService.getQualityCheckRun(scanId, runId, userDetails.getId(), userDetails.getRole());
        return ResponseEntity.ok(response);
    }
}

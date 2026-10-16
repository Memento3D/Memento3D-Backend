package com.memento3d.quality.api;

import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.quality.api.response.ImageQualityDetailResponse;
import com.memento3d.quality.api.response.QualityCheckRunSummaryResponse;
import com.memento3d.quality.api.response.QualityOverrideResponse;
import com.memento3d.quality.application.service.QualityRunService;
import com.memento3d.shared.api.response.ErrorResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality-runs")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Quality Runs", description = "Quality check runs and per-image inspection results endpoints")
@SecurityRequirement(name = "BearerAuth")
public class QualityRunController {

    private final QualityRunService qualityRunService;

    // -------------------------------------------------------------------------
    // GET /api/v1/quality-runs/{runId}
    // -------------------------------------------------------------------------
    @GetMapping({"/{runId}", "/{runId:[0-9a-fA-F\\-]{36}}"})
    @Operation(
            summary = "Get quality check run summary details",
            description = "Retrieves score, status, and summary statistics of a specific quality run."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quality run retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QualityCheckRunSummaryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Quality run not found or IDOR denied")
    })
    public ResponseEntity<QualityCheckRunSummaryResponse> getQualityRun(
            @PathVariable("runId") UUID runId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("GET quality run ID: {} by user: {}", runId, currentUser.getId());
        QualityCheckRunSummaryResponse response = qualityRunService.getQualityRunById(
                runId, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/quality-runs/{runId}/images
    // -------------------------------------------------------------------------
    @GetMapping({"/{runId}/images", "/{run_id}/images"})
    @Operation(
            summary = "List all image inspection results in a quality check run",
            description = "Returns complete technical quality details for every image evaluated in the specified quality run."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image quality inspection results retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImageQualityDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Quality run not found or IDOR denied")
    })
    public ResponseEntity<List<ImageQualityDetailResponse>> getQualityRunImages(
            @PathVariable("runId") UUID runId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("GET images for quality run ID: {} by user: {}", runId, currentUser.getId());
        List<ImageQualityDetailResponse> response = qualityRunService.getQualityRunImages(
                runId, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/quality-runs/{runId}/images/{imageId}
    // -------------------------------------------------------------------------
    @GetMapping({
            "/{runId}/images/{imageId}",
            "/{run_id}/images/{image_id}"
    })
    @Operation(
            summary = "Get detailed technical inspection result for a specific image in a quality check run",
            description = "Returns complete technical quality metrics (sharpness/blur score, mean brightness, exposure status, " +
                    "duplicate status, duplicate_of details, feature count, overlap scores, SNR noise score) " +
                    "and failure remediation suggestions, along with original image storage path/URL for FE zooming and histogram display."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image quality inspection details retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImageQualityDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Quality run or image quality result not found, or IDOR access denied",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ImageQualityDetailResponse> getImageQualityDetail(
            @PathVariable(value = "runId", required = false) UUID runIdCamel,
            @PathVariable(value = "run_id", required = false) UUID runIdSnake,
            @PathVariable(value = "imageId", required = false) UUID imageIdCamel,
            @PathVariable(value = "image_id", required = false) UUID imageIdSnake,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID runId = runIdCamel != null ? runIdCamel : runIdSnake;
        UUID imageId = imageIdCamel != null ? imageIdCamel : imageIdSnake;

        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Received request to get image quality detail for run ID: {}, image ID: {} by user: {}, role: {}",
                runId, imageId, currentUser.getId(), currentUser.getRole());

        ImageQualityDetailResponse response = qualityRunService.getImageQualityDetail(
                runId, imageId, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/quality-runs/{runId}/overrides
    // -------------------------------------------------------------------------
    @PostMapping({"/{runId}/overrides", "/{runId}/override"})
    @Operation(
            summary = "Override an amber quality run directly",
            description = "Allows the user to accept the risk of an amber quality score. Green -> 400, red -> 403. Idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Override accepted; scan status is 'ready'",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QualityOverrideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Override not applicable"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Red score cannot be overridden"),
            @ApiResponse(responseCode = "404", description = "Quality run not found or access denied")
    })
    public ResponseEntity<QualityOverrideResponse> overrideQualityRun(
            @PathVariable("runId") UUID runId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Override quality run {} by user {}", runId, currentUser.getId());
        QualityOverrideResponse response = qualityRunService.overrideQualityRunDirect(
                runId, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    private CustomUserDetails getAuthenticatedUserDetails(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            return cud;
        }
        throw new InvalidTokenException("Invalid user details in security context");
    }
}

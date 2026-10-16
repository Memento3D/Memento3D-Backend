package com.memento3d.reconstruction.api;

import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.models.api.response.ModelOutputResponse;
import com.memento3d.reconstruction.api.request.CreateReconstructionJobRequest;
import com.memento3d.reconstruction.api.response.ReconstructionJobResponse;
import com.memento3d.reconstruction.application.service.ReconstructionJobService;
import com.memento3d.shared.api.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Reconstruction Jobs",
        description = "Submit 3D reconstruction, retrieve job status, outputs, and job cancellations"
)
@SecurityRequirement(name = "BearerAuth")
public class ReconstructionJobController {

    private final ReconstructionJobService reconstructionJobService;

    // -------------------------------------------------------------------------
    // POST /api/v1/scans/{scanId}/reconstruction-jobs (Submit Job)
    // -------------------------------------------------------------------------
    @PostMapping({
            "/api/v1/scans/{scanId}/reconstruction-jobs",
            "/api/v1/scans/{scanId}/job"
    })
    @Operation(summary = "Submit a 3D reconstruction job for a scan session")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Job accepted and queued for 3D reconstruction",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReconstructionJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request or missing input_image_id",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Scan or image not found"),
            @ApiResponse(responseCode = "409", description = "Scan already has an active job or invalid state",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReconstructionJobResponse> submitJob(
            @PathVariable("scanId") UUID scanId,
            @Valid @RequestBody CreateReconstructionJobRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Submitting reconstruction job for scan {} by user {}", scanId, currentUser.getId());

        ReconstructionJobResponse response = reconstructionJobService.submitJob(
                scanId,
                request,
                currentUser.getId(),
                currentUser.getRole()
        );

        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/reconstruction-jobs/" + response.getJobId()))
                .body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/scans/{scanId}/reconstruction-jobs (Get Latest Job for Scan)
    // -------------------------------------------------------------------------
    @GetMapping({
            "/api/v1/scans/{scanId}/reconstruction-jobs",
            "/api/v1/scans/{scanId}/job"
    })
    @Operation(summary = "Get the latest reconstruction job for a scan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reconstruction job details retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReconstructionJobResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Job not found or scan not found")
    })
    public ResponseEntity<ReconstructionJobResponse> getLatestJob(
            @PathVariable("scanId") UUID scanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Fetching latest reconstruction job for scan {} by user {}", scanId, currentUser.getId());

        ReconstructionJobResponse response = reconstructionJobService.getLatestJob(
                scanId,
                currentUser.getId(),
                currentUser.getRole()
        );

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/reconstruction-jobs/{jobId} (Get Job Details by ID)
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/reconstruction-jobs/{jobId}")
    @Operation(summary = "Get reconstruction job by Job ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job details retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReconstructionJobResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Job not found or IDOR denied")
    })
    public ResponseEntity<ReconstructionJobResponse> getJobById(
            @PathVariable("jobId") UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Fetching reconstruction job {} by user {}", jobId, currentUser.getId());

        ReconstructionJobResponse response = reconstructionJobService.getJobById(
                jobId,
                currentUser.getId(),
                currentUser.getRole()
        );

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/reconstruction-jobs/{jobId}/outputs (Get Job 3D Model Outputs)
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/reconstruction-jobs/{jobId}/outputs")
    @Operation(summary = "Get 3D model outputs produced by a reconstruction job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Outputs retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ModelOutputResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Job not found or IDOR denied")
    })
    public ResponseEntity<List<ModelOutputResponse>> getJobOutputs(
            @PathVariable("jobId") UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Fetching outputs for job {} by user {}", jobId, currentUser.getId());

        List<ModelOutputResponse> response = reconstructionJobService.getJobOutputs(
                jobId,
                currentUser.getId(),
                currentUser.getRole()
        );

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/reconstruction-jobs/{jobId}/cancellations (Cancel Job)
    // -------------------------------------------------------------------------
    @PostMapping("/api/v1/reconstruction-jobs/{jobId}/cancellations")
    @Operation(summary = "Cancel an in-progress or queued reconstruction job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job cancelled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReconstructionJobResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Job not found or IDOR denied"),
            @ApiResponse(responseCode = "409", description = "Conflict: cannot cancel completed or failed job")
    })
    public ResponseEntity<ReconstructionJobResponse> cancelJob(
            @PathVariable("jobId") UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Cancelling job {} by user {}", jobId, currentUser.getId());

        ReconstructionJobResponse response = reconstructionJobService.cancelJob(
                jobId,
                currentUser.getId(),
                currentUser.getRole()
        );

        return ResponseEntity.ok(response);
    }

    private CustomUserDetails requireAuthenticatedUser(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }

        if (authentication.getPrincipal() instanceof CustomUserDetails currentUser && currentUser.getId() != null) {
            return currentUser;
        }

        throw new InvalidTokenException("Invalid user details in security context");
    }
}
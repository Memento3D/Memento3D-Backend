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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.memento3d.reconstruction.api.response.ReconstructionCapabilitiesResponse;
import com.memento3d.reconstruction.domain.port.ReconstructionProvider;
import com.memento3d.reconstruction.domain.port.ReconstructionProviderRegistry;

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
    private final ReconstructionProviderRegistry providerRegistry;

    @GetMapping("/api/v1/reconstruction/capabilities")
    @Operation(summary = "Get AI reconstruction provider capabilities and photo guidelines")
    public ResponseEntity<ReconstructionCapabilitiesResponse> getCapabilities() {
        ReconstructionProvider provider = providerRegistry.getDefaultProvider();
        return ResponseEntity.ok(ReconstructionCapabilitiesResponse.builder()
                .provider(provider.getProviderCode())
                .minImages(provider.getMinImages())
                .maxImages(provider.getMaxImages())
                .supportedFormats(List.of("image/jpeg", "image/png", "image/webp"))
                .maxFileSizeMb(20)
                .guidelines("Hỗ trợ chụp từ 1 ảnh rõ nét bằng camera laptop hoặc tải 1-50 ảnh đa góc. AI Hunyuan3D-2 tối ưu cho chủ thể chân dung, tượng nhỏ, đồ lưu niệm thủ công trong điều kiện đủ sáng.")
                .build());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/reconstruction-jobs/demo & /api/v1/scans/demo/reconstruction-jobs
    // -------------------------------------------------------------------------
    @GetMapping({"/api/v1/reconstruction-jobs/demo", "/api/v1/scans/demo/reconstruction-jobs"})
    @Operation(summary = "Get gallery demo model metadata for illustration purposes")
    public ResponseEntity<ReconstructionJobResponse> getDemoJob() {
        return ResponseEntity.ok(ReconstructionJobResponse.builder()
                .jobId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .scanId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .status(com.memento3d.reconstruction.domain.model.ReconstructionJobStatus.done)
                .progress(100)
                .profile("DEMO_SAMPLE")
                .errorMessage("Mô hình mẫu tham khảo phòng trưng bày")
                .createdAt(java.time.Instant.now())
                .completedAt(java.time.Instant.now())
                .build());
    }

    @GetMapping({"/api/v1/reconstruction-jobs/demo/outputs", "/api/v1/reconstruction-jobs/00000000-0000-0000-0000-000000000000/outputs"})
    @Operation(summary = "Get gallery demo 3D model outputs")
    public ResponseEntity<List<com.memento3d.models.api.response.ModelOutputResponse>> getDemoOutputs() {
        return ResponseEntity.ok(List.of(
                com.memento3d.models.api.response.ModelOutputResponse.builder()
                        .outputId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                        .jobId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                        .format("glb")
                        .deliveryUrl("https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Duck/glTF-Binary/Duck.glb")
                        .fileSizeBytes(117768L)
                        .createdAt(java.time.Instant.now())
                        .build()
        ));
    }

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
    // GET /api/v1/reconstructions/{jobId} & /api/v1/reconstruction-jobs/{jobId} (Get Job Details by ID)
    // -------------------------------------------------------------------------
    @GetMapping({"/api/v1/reconstructions/{jobId}", "/api/v1/reconstruction-jobs/{jobId}"})
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
    // GET /api/v1/reconstruction-jobs (List Jobs for User / Admin)
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/reconstruction-jobs")
    @Operation(summary = "List reconstruction jobs", description = "Retrieves a paginated list of 3D reconstruction jobs for current user (or all jobs if Admin/Operator)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<org.springframework.data.domain.Page<ReconstructionJobResponse>> listJobs(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Listing reconstruction jobs for user {} (page: {}, limit: {})", currentUser.getId(), page, limit);

        org.springframework.data.domain.Page<ReconstructionJobResponse> response =
                reconstructionJobService.listJobs(currentUser.getId(), currentUser.getRole(), page, limit);

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/reconstruction-jobs/{jobId}/cancellations & DELETE /api/v1/reconstruction-jobs/{jobId} (Cancel Job)
    // -------------------------------------------------------------------------
    @RequestMapping(
            value = {"/api/v1/reconstruction-jobs/{jobId}/cancellations", "/api/v1/reconstruction-jobs/{jobId}"},
            method = {RequestMethod.POST, RequestMethod.DELETE}
    )
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

    // -------------------------------------------------------------------------
    // POST /api/v1/reconstructions/previews (BE-03)
    // -------------------------------------------------------------------------
    @PostMapping("/api/v1/reconstructions/previews")
    @Operation(summary = "Submit a free 3D preview reconstruction job")
    public ResponseEntity<ReconstructionJobResponse> submitPreview(
            @Valid @RequestBody com.memento3d.reconstruction.api.request.CreatePreviewJobRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Submitting preview job for scan {} by user {}", request.getScanId(), currentUser.getId());

        ReconstructionJobResponse response = reconstructionJobService.submitPreviewJob(
                request,
                currentUser.getId(),
                currentUser.getRole()
        );

        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/reconstructions/" + response.getJobId()))
                .body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/reconstructions/quotas/me (BE-03)
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/reconstructions/quotas/me")
    @Operation(summary = "Get user preview quota", description = "Checks remaining free 3D preview reconstruction quota")
    public ResponseEntity<com.memento3d.reconstruction.api.response.QuotaResponse> getMyQuota(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(reconstructionJobService.getMyQuota(currentUser.getId()));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/reconstructions/{jobId}/preview (BE-03)
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/reconstructions/{jobId}/preview")
    @Operation(summary = "Get 3D preview details", description = "Returns watermark/viewer URL for preview job; hides high-resolution direct download")
    public ResponseEntity<com.memento3d.reconstruction.api.response.PreviewJobDetailsResponse> getPreview(
            @PathVariable("jobId") UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(reconstructionJobService.getPreviewDetails(jobId, currentUser.getId(), currentUser.getRole()));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/orders/{orderId}/reconstructions (BE-04)
    // -------------------------------------------------------------------------
    @PostMapping("/api/v1/orders/{orderId}/reconstructions")
    @Operation(summary = "Trigger high quality reconstruction for paid order")
    public ResponseEntity<ReconstructionJobResponse> submitPremiumReconstruction(
            @PathVariable("orderId") UUID orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("Triggering premium reconstruction for order {} by user {}", orderId, currentUser.getId());

        ReconstructionJobResponse response = reconstructionJobService.submitPremiumJob(
                orderId,
                currentUser.getId(),
                currentUser.getRole()
        );

        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/reconstructions/" + response.getJobId()))
                .body(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/admin/reconstructions/{jobId}/retry (BE-04)
    // -------------------------------------------------------------------------
    @PostMapping("/api/v1/admin/reconstructions/{jobId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Admin retry failed reconstruction job")
    public ResponseEntity<ReconstructionJobResponse> retryJob(
            @PathVariable("jobId") UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(reconstructionJobService.retryJob(jobId, currentUser.getId(), currentUser.getRole()));
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
package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ErrorResponse;
import com.memeto.team.memeto3d.dto.ScanImageListResponse;
import com.memeto.team.memeto3d.dto.ScanImageUploadResponse;
import com.memeto.team.memeto3d.dto.ScanListResponse;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.dto.UpdateScanRequest;
import com.memeto.team.memeto3d.exception.InvalidTokenException;
import com.memeto.team.memeto3d.security.CustomUserDetails;
import com.memeto.team.memeto3d.service.ScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Scans", description = "Scan session management endpoints")
public class ScanController {

    private final ScanService scanService;

    @GetMapping
    @Operation(
            summary = "Get user scans with pagination, filtering, and sorting",
            description = "Retrieves paginated scan sessions belonging to the authenticated user. Supports filtering by status, pagination (page, limit up to 100), and sorting (default created_at DESC).",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scans retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScanListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid parameters (e.g. invalid status, invalid page/limit, disallowed sort property)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ScanListResponse> getScans(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") @Min(value = 1, message = "Page must be at least 1") Integer page,
            @RequestParam(name = "limit", defaultValue = "20") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 100, message = "Limit cannot exceed 100") Integer limit,
            @RequestParam(name = "sort", required = false) String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = getAuthenticatedUserId(userDetails);
        log.info("Received request to retrieve scans for user ID: {}, status: {}, page: {}, limit: {}, sort: {}",
                userId, status, page, limit, sort);

        ScanListResponse response = scanService.getScans(userId, status, page, limit, sort);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(
            summary = "Create a new scan session",
            description = "Initializes a new 3D scan session with draft status for the authenticated user",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Scan session successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (e.g. empty name, invalid input_mode)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Storage quota exceeded",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ScanResponse> createScan(
            @Valid @RequestBody CreateScanRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = getAuthenticatedUserId(userDetails);
        log.info("Received request to create scan '{}' for user ID: {}", request.getName(), userId);

        ScanResponse response = scanService.createScan(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get scan session details by ID",
            description = "Retrieves full details of a 3D scan session for the authenticated owner or admin/operator",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan session details successfully retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScanResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan session not found or access denied (IDOR protection)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ScanResponse> getScanById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Received request to get scan ID: {} for user ID: {} with role: {}", id, currentUser.getId(), currentUser.getRole());

        ScanResponse response = scanService.getScanById(id, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Update scan session details",
            description = "Updates editable metadata (name, description, notes) of a scan session. Blocked if status is uploading or processing.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan session successfully updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (e.g. empty name)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan session not found or access denied (IDOR protection)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict: scan is in uploading or processing status",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ScanResponse> updateScan(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateScanRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Received request to update scan ID: {} for user ID: {} with role: {}", id, currentUser.getId(), currentUser.getRole());

        ScanResponse response = scanService.updateScan(id, request, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Soft-delete a scan session",
            description = "Marks a scan session as deleted (soft-delete) by setting status='deleted' and deleted_at=NOW(). " +
                    "The scan and its associated files are NOT immediately removed; hard deletion happens after 90 days per the retention policy (FR-177). " +
                    "Storage quota is NOT released immediately to prevent quota-gaming.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Scan session successfully deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan session not found or access denied (IDOR protection)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict: scan is currently in 'processing' status and cannot be deleted",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteScan(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Received request to delete scan ID: {} for user ID: {} with role: {}", id, currentUser.getId(), currentUser.getRole());

        scanService.deleteScan(id, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // BE1 — GET /api/v1/scans/{id}/images
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/images")
    @Operation(
            summary = "List images in a scan session (Review Gallery)",
            description = "Returns all images (incl. upload_status=failed) sorted by sort_order ASC " +
                    "then created_at ASC. Only the scan owner or admin/operator can access (IDOR → 404).",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image list retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScanImageListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan not found or IDOR denied",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ScanImageListResponse> getScanImages(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", defaultValue = "1")
            @Min(value = 1, message = "Page must be at least 1") Integer page,
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit cannot exceed 100") Integer limit,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("List images for scan ID: {} by user: {} role: {}",
                id, currentUser.getId(), currentUser.getRole());
        ScanImageListResponse response =
                scanService.getScanImages(id, currentUser.getId(), currentUser.getRole(), page, limit);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // BE2 — POST /api/v1/scans/{id}/images
    // -------------------------------------------------------------------------

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a supplemental image to an existing scan (UC-04 chụp bù)",
            description = "Appends a new image to an existing scan session. Validates ownership, " +
                    "scan status (blocked when processing/done), file format (jpg/jpeg/png), " +
                    "file size (≤ 20 MB), and storage quota. " +
                    "Sets needs_quality_check=true when a prior QC run exists so the QC engine " +
                    "re-checks only the new image on the next run.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image uploaded successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScanImageUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size > 20 MB",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Storage quota exceeded",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan not found or IDOR denied",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Scan is processing or done — cannot add images",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ScanImageUploadResponse> addImageToScan(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Upload image '{}' to scan ID: {} by user: {} role: {}",
                file != null ? file.getOriginalFilename() : "null",
                id, currentUser.getId(), currentUser.getRole());
        ScanImageUploadResponse response =
                scanService.addImageToScan(id, file, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // POST /api/v1/scans/{id}/video
    // -------------------------------------------------------------------------

    @PostMapping(value = "/{id}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a video to an existing scan",
            description = "Uploads a video file (.mp4, .mov, .avi) to an existing scan session. Validates ownership, " +
                    "scan status, input mode (must be video), file format, file size (≤ 2 GB), and storage quota.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Video uploaded successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.memeto.team.memeto3d.dto.ScanVideoUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size > 2 GB",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Storage quota exceeded",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan not found or IDOR denied",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Scan is not in video mode or status blocked",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<com.memeto.team.memeto3d.dto.ScanVideoUploadResponse> addVideoToScan(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Upload video '{}' to scan ID: {} by user: {} role: {}",
                file != null ? file.getOriginalFilename() : "null",
                id, currentUser.getId(), currentUser.getRole());
        com.memeto.team.memeto3d.dto.ScanVideoUploadResponse response =
                scanService.addVideoToScan(id, file, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/scans/{id}/video
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/video")
    @Operation(
            summary = "Get video metadata for a scan",
            description = "Retrieves video metadata (duration, resolution, fps, upload status, storage path) for a scan session. Returns 404 if no video is found.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Video metadata retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.memeto.team.memeto3d.dto.ScanVideoMetadataResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan or video not found, or IDOR denied",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<com.memeto.team.memeto3d.dto.ScanVideoMetadataResponse> getScanVideoMetadata(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Get video metadata for scan ID: {} by user: {} role: {}",
                id, currentUser.getId(), currentUser.getRole());
        com.memeto.team.memeto3d.dto.ScanVideoMetadataResponse response =
                scanService.getScanVideoMetadata(id, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // BE3 — DELETE /api/v1/scans/{id}/images/{image_id}
    // -------------------------------------------------------------------------

    @DeleteMapping("/{id}/images/{image_id}")
    @Operation(
            summary = "Delete a specific image from a scan (UC-05 xoá ảnh)",
            description = "Removes a single image from a scan before submitting quality check or after receiving quality report. " +
                    "Hard-deletes the image (not soft-delete) since images don't require long-term retention. " +
                    "Updates scan counters and user storage quota. Blocked if scan is in 'processing' status.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Image successfully deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan or image not found, or access denied (IDOR protection)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict: scan is in 'processing' status",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteImageFromScan(
            @PathVariable("id") UUID id,
            @PathVariable("image_id") UUID imageId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Delete image ID: {} from scan ID: {} by user: {} role: {}",
                imageId, id, currentUser.getId(), currentUser.getRole());

        scanService.deleteImageFromScan(id, imageId, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.noContent().build();
    }
    // -------------------------------------------------------------------------
    // GET /api/v1/scans/{id}/quality-runs
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/quality-runs")
    @Operation(
            summary = "Get quality check runs history for a scan",
            description = "Returns the history of quality check runs for a scan, sorted by run_number DESC (newest first). " +
                    "If the scan has never run a quality check, an empty array is returned. Only the scan owner or admin/operator can access.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quality check runs retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.memeto.team.memeto3d.dto.QualityCheckRunSummaryResponse.class, type = "array"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan not found or access denied (IDOR protection)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<java.util.List<com.memeto.team.memeto3d.dto.QualityCheckRunSummaryResponse>> getQualityCheckRuns(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Get quality check runs for scan ID: {} by user: {} role: {}",
                id, currentUser.getId(), currentUser.getRole());

        java.util.List<com.memeto.team.memeto3d.dto.QualityCheckRunSummaryResponse> response =
                scanService.getQualityCheckRuns(id, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/scans/{id}/quality-runs/{run_id}/override
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/quality-runs/{run_id}/override")
    @Operation(
            summary = "Override an amber quality run and proceed to reconstruction",
            description = "Allows the user to accept the risk of an amber quality score. Green -> 400, red -> 403. " +
                    "Idempotent: repeating the call for the same run returns the same result without a duplicate audit entry.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Override accepted; scan status is 'ready'",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.memeto.team.memeto3d.dto.QualityOverrideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Override not applicable (green or not-yet-scored run)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Red score cannot be overridden",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan/run not found or access denied",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Scan state does not allow override",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<com.memeto.team.memeto3d.dto.QualityOverrideResponse> overrideQualityRun(
            @PathVariable("id") UUID id,
            @PathVariable("run_id") UUID runId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Override quality run {} for scan {} by user {}", runId, id, currentUser.getId());
        return ResponseEntity.ok(
                scanService.overrideQualityRun(id, runId, currentUser.getId(), currentUser.getRole()));
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

    private UUID getAuthenticatedUserId(CustomUserDetails userDetails) {
        return getAuthenticatedUserDetails(userDetails).getId();
    }
}

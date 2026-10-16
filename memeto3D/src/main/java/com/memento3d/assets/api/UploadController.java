package com.memento3d.assets.api;

import com.memento3d.assets.domain.model.UploadChunk;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.scans.domain.model.Scan;

import com.memento3d.shared.api.response.ErrorResponse;
import com.memento3d.assets.api.response.UploadStatusResponse;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.assets.application.service.UploadChunkService;
import com.memento3d.assets.application.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST controller for chunked-upload session management.
 *
 * <ul>
 *   <li>{@code POST /api/v1/uploads/{id}/chunks/{index}} — receive one chunk</li>
 *   <li>{@code GET  /api/v1/uploads/{id}/status}         — query session status for resume</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chunked Uploads", description = "Endpoints for handling large file chunked uploads")
public class UploadController {

    /** Handles chunk file I/O and merge logic. */
    private final UploadChunkService uploadChunkService;

    /** Handles upload session status queries (resume support). */
    private final UploadService uploadService;

    @RequestMapping(
            value = {"/{uploadId}/chunks/{chunkIndex}", "/{id}/chunks/{index}"},
            method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(
        summary = "Upload a file chunk (POST or PUT)",
        description = "Uploads a specific chunk of a file. Supports retry on duplicate chunks. " +
                      "Merges chunks when the last one is received."
    )
    public ResponseEntity<?> uploadChunk(
            @PathVariable(value = "uploadId", required = false) UUID uploadId,
            @PathVariable(value = "id", required = false) UUID id,
            @PathVariable(value = "chunkIndex", required = false) Integer chunkIndex,
            @PathVariable(value = "index", required = false) Integer index,
            @RequestParam("file") MultipartFile file
    ) {
        UUID effectiveId = uploadId != null ? uploadId : id;
        int effectiveIndex = chunkIndex != null ? chunkIndex : (index != null ? index : 0);
        log.info("Received chunk {} for upload session {}", effectiveIndex, effectiveId);
        uploadChunkService.uploadChunk(effectiveId, effectiveIndex, file);
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/uploads/{id}/status and GET /api/v1/uploads/{uploadId}
    // -------------------------------------------------------------------------

    @GetMapping({"/{id}/status", "/{id}"})
    @Operation(
        summary = "Get chunked upload session status",
        description = """
            Returns the current upload status for a chunked upload session,
            including the total number of expected chunks, how many have been
            successfully received, and the sorted list of chunk indexes that
            are still missing.

            Clients MUST call this endpoint after a network interruption to
            resume the upload from the correct position instead of restarting
            from the beginning.

            - **Session not found** -> 404
            - **Session completed** -> `upload_status: "completed"`, no `missing_indexes`
            - **Session in progress** -> `missing_indexes` contains the chunks to re-upload
            """,
        security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Upload status retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UploadStatusResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized: missing, invalid, or expired JWT",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Upload session not found, scan not found, or IDOR denied",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<UploadStatusResponse> getUploadStatus(
            @PathVariable("id")
            @Parameter(description = "Scan UUID that owns this upload session")
            UUID id,

            @RequestParam(name = "file_reference", required = false)
            @Parameter(
                description = "Optional filename or session key to disambiguate when " +
                              "the same scan has multiple upload sessions. " +
                              "When omitted the most-recent session is returned."
            )
            String fileReference,

            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("GET upload status: scanId={}, fileReference={}, userId={}, role={}",
                id, fileReference, currentUser.getId(), currentUser.getRole());

        UploadStatusResponse response = uploadService.getUploadStatus(
                id, fileReference, currentUser.getId(), currentUser.getRole());

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Auth helpers
    // -------------------------------------------------------------------------

    private CustomUserDetails getAuthenticatedUserDetails(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            return cud;
        }
        throw new InvalidTokenException("Invalid user details in security context");
    }
}
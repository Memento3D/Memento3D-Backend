package com.memento3d.models.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.models.api.request.CreateModelRatingRequest;
import com.memento3d.models.api.response.ModelRatingResponse;
import com.memento3d.models.application.service.ModelRatingService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Model Ratings", description = "Endpoints for rating 3D reconstruction quality")
public class ModelRatingController {

    private final ModelRatingService modelRatingService;

    // -------------------------------------------------------------------------
    // POST /api/v1/scans/{scanId}/ratings (Create or Update Rating)
    // -------------------------------------------------------------------------
    @PostMapping({"/{scanId}/ratings", "/{scanId}/rating"})
    @Operation(
            summary = "Create or update model 3D quality rating",
            description = "Submits a rating (1 to 5 stars) and optional comment for a scan model. " +
                    "If the user has already rated this scan, the existing rating is updated instead of creating a duplicate. " +
                    "Allows both authenticated users and guest preview visitors."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model rating created or updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ModelRatingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid rating (outside range 1-5 or missing mandatory fields)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan session not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ModelRatingResponse> createOrUpdateRating(
            @PathVariable("scanId") UUID scanId,
            @Valid @RequestBody CreateModelRatingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = getOptionalUserId(userDetails);
        log.info("Received rating request for scan ID: {}, rating: {}, user ID: {}", scanId, request.getRating(), userId);

        ModelRatingResponse response = modelRatingService.createOrUpdateRating(scanId, request, userId);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/scans/{scanId}/ratings (List Ratings)
    // -------------------------------------------------------------------------
    @GetMapping({"/{scanId}/ratings", "/{scanId}/rating"})
    @Operation(
            summary = "Get ratings for a scan model",
            description = "Retrieves all user feedback and ratings submitted for the 3D model produced from this scan."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ratings retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ModelRatingResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan session not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ModelRatingResponse>> getScanRatings(
            @PathVariable("scanId") UUID scanId
    ) {
        log.info("Received request to get ratings for scan ID: {}", scanId);
        List<ModelRatingResponse> response = modelRatingService.getScanRatings(scanId);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/scans/{scanId}/rating (Delete My Rating)
    // -------------------------------------------------------------------------
    @DeleteMapping({"/{scanId}/rating", "/{scanId}/ratings"})
    @Operation(
            summary = "Delete user rating for a scan model",
            description = "Removes the authenticated user's rating for the specified scan model.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rating successfully deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Scan session not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteMyRating(
            @PathVariable("scanId") UUID scanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = getOptionalUserId(userDetails);
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Must be authenticated to delete rating");
        }
        log.info("Received request to delete rating for scan ID: {}, user ID: {}", scanId, userId);
        modelRatingService.deleteMyRating(scanId, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getOptionalUserId(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails.getId();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        return null;
    }
}

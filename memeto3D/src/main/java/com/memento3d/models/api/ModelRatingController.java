package com.memento3d.models.api;

import com.memento3d.identity.domain.model.User;
import com.memento3d.scans.domain.model.Scan;

import com.memento3d.models.api.request.CreateModelRatingRequest;
import com.memento3d.shared.api.response.ErrorResponse;
import com.memento3d.models.api.response.ModelRatingResponse;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.models.application.service.ModelRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Model Ratings", description = "Endpoints for rating 3D reconstruction quality")
public class ModelRatingController {

    private final ModelRatingService modelRatingService;

    @PostMapping({"/{scan_id}/ratings", "/{scan_id}/rating"})
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
            @PathVariable("scan_id") UUID scanId,
            @Valid @RequestBody CreateModelRatingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = getOptionalUserId(userDetails);
        log.info("Received rating request for scan ID: {}, rating: {}, user ID: {}", scanId, request.getRating(), userId);

        ModelRatingResponse response = modelRatingService.createOrUpdateRating(scanId, request, userId);
        return ResponseEntity.ok(response);
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

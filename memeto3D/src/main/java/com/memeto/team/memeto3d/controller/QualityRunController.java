package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.ErrorResponse;
import com.memeto.team.memeto3d.dto.ImageQualityDetailResponse;
import com.memeto.team.memeto3d.exception.InvalidTokenException;
import com.memeto.team.memeto3d.security.CustomUserDetails;
import com.memeto.team.memeto3d.service.QualityRunService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality-runs")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Quality Runs", description = "Quality check runs and per-image inspection results endpoints")
public class QualityRunController {

    private final QualityRunService qualityRunService;

    @GetMapping("/{run_id}/images/{image_id}")
    @Operation(
            summary = "Get detailed technical inspection result for a specific image in a quality check run",
            description = "Returns complete technical quality metrics (sharpness/blur score, mean brightness, exposure status, " +
                    "duplicate status, duplicate_of details, feature count, overlap scores, SNR noise score) " +
                    "and failure remediation suggestions, along with original image storage path/URL for FE zooming and histogram display.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image quality inspection details retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ImageQualityDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Quality run or image quality result not found, or IDOR access denied",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ImageQualityDetailResponse> getImageQualityDetail(
            @PathVariable("run_id") UUID runId,
            @PathVariable("image_id") UUID imageId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = getAuthenticatedUserDetails(userDetails);
        log.info("Received request to get image quality detail for run ID: {}, image ID: {} by user: {}, role: {}",
                runId, imageId, currentUser.getId(), currentUser.getRole());

        ImageQualityDetailResponse response = qualityRunService.getImageQualityDetail(
                runId, imageId, currentUser.getId(), currentUser.getRole());
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

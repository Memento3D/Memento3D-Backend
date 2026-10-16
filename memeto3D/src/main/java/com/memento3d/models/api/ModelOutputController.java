package com.memento3d.models.api;

import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.models.api.response.ModelOutputResponse;
import com.memento3d.models.application.service.ModelOutputService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Model Outputs", description = "3D model output retrieval and artifact inspection endpoints")
@SecurityRequirement(name = "BearerAuth")
public class ModelOutputController {

    private final ModelOutputService modelOutputService;

    // -------------------------------------------------------------------------
    // GET /api/v1/scans/{scanId}/outputs
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/scans/{scanId}/outputs")
    @Operation(summary = "Get available 3D model outputs for a scan session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model outputs retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ModelOutputResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Scan session not found or IDOR denied")
    })
    public ResponseEntity<List<ModelOutputResponse>> getScanOutputs(
            @PathVariable("scanId") UUID scanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("GET model outputs for scan {} by user {}", scanId, currentUser.getId());

        return ResponseEntity.ok(
                modelOutputService.getScanOutputs(
                        scanId,
                        currentUser.getId(),
                        currentUser.getRole()
                )
        );
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/models/{modelId}
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/models/{modelId}")
    @Operation(summary = "Get specific 3D model output artifact by model ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model output retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ModelOutputResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Model not found or access denied")
    })
    public ResponseEntity<ModelOutputResponse> getModelById(
            @PathVariable("modelId") UUID modelId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(userDetails);
        log.info("GET model output {} by user {}", modelId, currentUser.getId());

        return ResponseEntity.ok(
                modelOutputService.getModelOutputById(
                        modelId,
                        currentUser.getId(),
                        currentUser.getRole()
                )
        );
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
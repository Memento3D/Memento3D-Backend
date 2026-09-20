package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ErrorResponse;
import com.memeto.team.memeto3d.dto.ScanResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scans", description = "Scan session management endpoints")
public class ScanController {

    private final ScanService scanService;

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

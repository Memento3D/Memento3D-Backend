package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ErrorResponse;
import com.memeto.team.memeto3d.dto.ScanListResponse;
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

import java.util.UUID;

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

    private UUID getAuthenticatedUserId(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails.getId();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        throw new InvalidTokenException("Invalid user details in security context");
    }
}

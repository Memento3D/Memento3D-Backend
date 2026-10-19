package com.memento3d.assets.api;

import com.memento3d.assets.api.response.DigitalAssetResponse;
import com.memento3d.assets.api.response.DownloadUrlResponse;
import com.memento3d.assets.application.service.DigitalAssetService;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Digital Assets", description = "Digital 3D models and short-lived download URL generation")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("isAuthenticated()")
public class DigitalAssetController {

    private final DigitalAssetService digitalAssetService;

    @GetMapping("/api/v1/me/digital-assets")
    @Operation(summary = "Get my digital assets", description = "Lists all high quality digital 3D models generated for the user")
    public ResponseEntity<List<DigitalAssetResponse>> getMyAssets(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(digitalAssetService.getMyDigitalAssets(userDetails.getId()));
    }

    @GetMapping("/api/v1/digital-assets/{id}")
    @Operation(summary = "Get digital asset details by ID")
    public ResponseEntity<DigitalAssetResponse> getAssetById(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(digitalAssetService.getDigitalAssetById(id, userDetails.getId(), userDetails.getRole()));
    }

    @PostMapping("/api/v1/digital-assets/{id}/download-url")
    @Operation(summary = "Generate signed download URL", description = "Generates a time-limited signed URL for downloading 3D model, verified against Entitlement")
    public ResponseEntity<DownloadUrlResponse> generateDownloadUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(digitalAssetService.generateDownloadUrl(id, userDetails.getId(), userDetails.getRole()));
    }
}

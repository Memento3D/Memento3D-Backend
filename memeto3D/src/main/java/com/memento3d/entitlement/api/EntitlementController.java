package com.memento3d.entitlement.api;

import com.memento3d.entitlement.application.service.EntitlementService;
import com.memento3d.entitlement.domain.model.Entitlement;
import com.memento3d.entitlement.domain.model.EntitlementType;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/me/entitlements", "/api/v1/entitlements"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Entitlements", description = "Access rights & permissions for premium 3D assets and features")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("isAuthenticated()")
public class EntitlementController {

    private final EntitlementService entitlementService;

    @GetMapping
    @Operation(summary = "Get my entitlements", description = "Lists all active entitlements granted to the current authenticated user")
    public ResponseEntity<List<Entitlement>> getMyEntitlements(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(entitlementService.getMyEntitlements(userDetails.getId()));
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify entitlement", description = "Checks whether current user has a specific entitlement on a resource")
    public ResponseEntity<Map<String, Boolean>> verifyEntitlement(
            @RequestParam UUID resourceId,
            @RequestParam EntitlementType type,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        boolean hasAccess = entitlementService.hasEntitlement(userDetails.getId(), resourceId, type);
        return ResponseEntity.ok(Map.of("hasEntitlement", hasAccess));
    }
}

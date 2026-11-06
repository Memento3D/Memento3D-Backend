package com.memento3d.customization.api;

import com.memento3d.customization.api.request.SaveCustomizationRequest;
import com.memento3d.customization.api.response.*;
import com.memento3d.customization.application.service.ModelCustomizationService;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @PreAuthorize("isAuthenticated()") @SecurityRequirement(name="BearerAuth")
@RequestMapping("/api/v1/models/{modelOutputId}")
public class ModelCustomizationController {
    private final ModelCustomizationService service;
    @GetMapping("/scene") @Operation(summary="Load a private custom scene, saved selection and fresh asset URLs for an immutable model OUTPUT ID")
    public ResponseEntity<ModelCustomizationSceneResponse> scene(@PathVariable UUID modelOutputId,@AuthenticationPrincipal CustomUserDetails user) {
        var principal=principal(user);return uncached(service.scene(modelOutputId,principal.getId(),principal.getRole()));
    }
    @GetMapping("/customization") @Operation(summary="Get the saved draft; unsaved outputs return defaults with revision 0")
    public ResponseEntity<CustomizationDraftResponse> draft(@PathVariable UUID modelOutputId,@AuthenticationPrincipal CustomUserDetails user) {
        var principal=principal(user);return uncached(service.getDraft(modelOutputId,principal.getId(),principal.getRole()));
    }
    @PutMapping("/customization") @Operation(summary="Save selection JSON with revision control; does not reconstruct or export a GLB")
    public ResponseEntity<CustomizationDraftResponse> save(@PathVariable UUID modelOutputId,@Valid @RequestBody SaveCustomizationRequest request,@AuthenticationPrincipal CustomUserDetails user) {
        var principal=principal(user);return uncached(service.save(modelOutputId,request,principal.getId(),principal.getRole()));
    }
    private CustomUserDetails principal(CustomUserDetails user) { if(user==null)throw new InvalidTokenException("Authenticated user is required");return user; }
    private <T> ResponseEntity<T> uncached(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}

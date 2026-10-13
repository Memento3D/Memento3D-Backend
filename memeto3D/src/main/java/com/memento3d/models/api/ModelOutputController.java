package com.memento3d.models.api;

import com.memento3d.identity.domain.model.User;
import com.memento3d.scans.domain.model.Scan;

import com.memento3d.models.api.response.ModelOutputResponse;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.models.application.service.ModelOutputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Tag(name = "Model Outputs")
@SecurityRequirement(name = "BearerAuth")
public class ModelOutputController {

    private final ModelOutputService modelOutputService;

    @GetMapping("/{id}/outputs")
    @Operation(summary = "Get available model outputs of a scan")
    public ResponseEntity<List<ModelOutputResponse>> getScanOutputs(
            @PathVariable("id") UUID scanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser =
                requireAuthenticatedUser(userDetails);

        return ResponseEntity.ok(
                modelOutputService.getScanOutputs(
                        scanId,
                        currentUser.getId(),
                        currentUser.getRole()
                )
        );
    }

    private CustomUserDetails requireAuthenticatedUser(
            CustomUserDetails userDetails
    ) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException(
                    "No authenticated user in security context"
            );
        }

        if (authentication.getPrincipal()
                instanceof CustomUserDetails currentUser
                && currentUser.getId() != null) {
            return currentUser;
        }

        throw new InvalidTokenException(
                "Invalid user details in security context"
        );
    }
}
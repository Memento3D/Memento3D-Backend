package com.memento3d.reconstruction.api;

import com.memento3d.identity.domain.model.User;
import com.memento3d.scans.domain.model.Scan;

import com.memento3d.reconstruction.api.request.CreateReconstructionJobRequest;
import com.memento3d.reconstruction.api.response.ReconstructionJobResponse;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.reconstruction.application.service.ReconstructionJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Tag(
        name = "Reconstruction Jobs",
        description = "Submit reconstruction and retrieve job status"
)
@SecurityRequirement(name = "BearerAuth")
public class ReconstructionJobController {

    private final ReconstructionJobService reconstructionJobService;

    @PostMapping("/{id}/job")
    @Operation(summary = "Submit a reconstruction job")
    public ResponseEntity<ReconstructionJobResponse> submitJob(
            @PathVariable("id") UUID scanId,
            @Valid @RequestBody CreateReconstructionJobRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(
                userDetails
        );

        ReconstructionJobResponse response =
                reconstructionJobService.submitJob(
                        scanId,
                        request,
                        currentUser.getId(),
                        currentUser.getRole()
                );

        return ResponseEntity.accepted()
                .location(URI.create(
                        "/api/v1/scans/" + scanId + "/job"
                ))
                .body(response);
    }

    @GetMapping("/{id}/job")
    @Operation(summary = "Get the latest reconstruction job of a scan")
    public ResponseEntity<ReconstructionJobResponse> getLatestJob(
            @PathVariable("id") UUID scanId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails currentUser = requireAuthenticatedUser(
                userDetails
        );

        ReconstructionJobResponse response =
                reconstructionJobService.getLatestJob(
                        scanId,
                        currentUser.getId(),
                        currentUser.getRole()
                );

        return ResponseEntity.ok(response);
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
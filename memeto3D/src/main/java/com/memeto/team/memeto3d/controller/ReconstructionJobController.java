package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.CreateReconstructionJobRequest;
import com.memeto.team.memeto3d.dto.ReconstructionJobResponse;
import com.memeto.team.memeto3d.exception.InvalidTokenException;
import com.memeto.team.memeto3d.security.CustomUserDetails;
import com.memeto.team.memeto3d.service.ReconstructionJobService;
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
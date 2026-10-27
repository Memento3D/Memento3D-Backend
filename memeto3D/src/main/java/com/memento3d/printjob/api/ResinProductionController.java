package com.memento3d.printjob.api;

import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.printjob.api.request.*;
import com.memento3d.printjob.api.response.ResinProductionJobResponse;
import com.memento3d.printjob.application.service.ResinProductionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/production/resin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Resin Production (ELEGOO Saturn 4 Ultra 16K)", description = "Slicing, machine handoff, post-processing, and QC inspection management")
@SecurityRequirement(name = "BearerAuth")
public class ResinProductionController {

    private final ResinProductionService resinProductionService;

    @PostMapping("/jobs/{orderItemId}/prepare")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Prepare production model and STL for resin manufacturing")
    public ResponseEntity<ResinProductionJobResponse> prepareProduction(
            @PathVariable("orderItemId") UUID orderItemId,
            @Valid @RequestBody PrepareResinProductionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(resinProductionService.prepareProductionJob(orderItemId, request, user.getId()));
    }

    @PostMapping("/jobs/{printJobId}/slicing")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Record CHITUBOX slice file (.goo/.ctb) and resin profile")
    public ResponseEntity<ResinProductionJobResponse> recordSlicing(
            @PathVariable("printJobId") UUID printJobId,
            @Valid @RequestBody RecordSlicingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(resinProductionService.recordSlicing(printJobId, request, user.getId()));
    }

    @PostMapping("/jobs/{printJobId}/handoff")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Record file handoff to ELEGOO Saturn 4 Ultra 16K (USB or ChituManager LAN)")
    public ResponseEntity<ResinProductionJobResponse> confirmHandoff(
            @PathVariable("printJobId") UUID printJobId,
            @Valid @RequestBody ConfirmHandoffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(resinProductionService.confirmHandoff(printJobId, request, user.getId()));
    }

    @PostMapping("/jobs/{printJobId}/start-printing")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Operator confirm printing has started physically on the machine")
    public ResponseEntity<ResinProductionJobResponse> confirmStartPrinting(
            @PathVariable("printJobId") UUID printJobId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(resinProductionService.confirmStartPrinting(printJobId, user.getId()));
    }

    @PostMapping("/jobs/{printJobId}/post-processing")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Update post-processing checklist (Wash, Support removal, UV Cure, Surface Finish)")
    public ResponseEntity<ResinProductionJobResponse> updatePostProcessing(
            @PathVariable("printJobId") UUID printJobId,
            @Valid @RequestBody PostProcessingChecklistRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(resinProductionService.updatePostProcessing(printJobId, request, user.getId()));
    }

    @PostMapping("/jobs/{printJobId}/qc")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Record physical QC check results for finished resin model")
    public ResponseEntity<ResinProductionJobResponse> recordQcResult(
            @PathVariable("printJobId") UUID printJobId,
            @Valid @RequestBody RecordQcResultRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(resinProductionService.recordQcResult(printJobId, request, user.getId()));
    }

    @PostMapping("/jobs/{printJobId}/reprint")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Create reprint attempt for failed job without charging customer")
    public ResponseEntity<ResinProductionJobResponse> createReprint(
            @PathVariable("printJobId") UUID printJobId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails user = requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(resinProductionService.createReprint(printJobId, user.getId()));
    }

    @GetMapping("/jobs/{printJobId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Get resin production job details")
    public ResponseEntity<ResinProductionJobResponse> getJobDetails(
            @PathVariable("printJobId") UUID printJobId
    ) {
        return ResponseEntity.ok(resinProductionService.getJobDetails(printJobId));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('OPERATOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "List resin production jobs")
    public ResponseEntity<List<ResinProductionJobResponse>> listJobs(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(resinProductionService.listResinJobs(page, limit));
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

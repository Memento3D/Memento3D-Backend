package com.memento3d.admin.controller;

import com.memento3d.admin.service.AdminReconstructionService;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.reconstruction.api.response.ReconstructionJobResponse;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reconstruction")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Reconstruction", description = "3D Reconstruction job pipeline monitoring and retry controls")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReconstructionController {

    private final AdminReconstructionService reconstructionService;

    @GetMapping("")
    @Operation(summary = "List all reconstruction jobs with status, tier filters and pagination")
    public ResponseEntity<Page<ReconstructionJobResponse>> listJobs(
            @RequestParam(name = "status", required = false) ReconstructionJobStatus status,
            @RequestParam(name = "tier", required = false) ReconstructionTier tier,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reconstructionService.listJobs(status, tier, pageRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reconstruction job detail and error trace")
    public ResponseEntity<ReconstructionJobResponse> getJobDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(reconstructionService.getJobDetail(id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry failed reconstruction job", description = "Idempotent retry for FAILED jobs only. RUNNING and DONE jobs cannot be retried.")
    public ResponseEntity<ReconstructionJobResponse> retryJob(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(reconstructionService.retryFailedJob(id, actorId));
    }
}

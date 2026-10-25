package com.memento3d.operations.reviewqueue.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.operations.reviewqueue.api.request.ResolveReviewRequest;
import com.memento3d.operations.reviewqueue.api.response.ManualReviewResponse;
import com.memento3d.operations.reviewqueue.application.service.ManualReviewService;
import com.memento3d.operations.reviewqueue.domain.model.ReviewPriority;
import com.memento3d.operations.reviewqueue.domain.model.ReviewStatus;
import com.memento3d.operations.reviewqueue.domain.model.ReviewType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews/manual")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Manual Review Queue", description = "Human-in-the-Loop exception review management for Admin and Manager")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ManualReviewController {

    private final ManualReviewService reviewService;

    @GetMapping
    @Operation(summary = "List manual review queue items", description = "Filter by status, review type, and priority with pagination")
    public ResponseEntity<Page<ManualReviewResponse>> listReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) ReviewType type,
            @RequestParam(required = false) ReviewPriority priority,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(reviewService.listReviews(status, type, priority, page, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get specific manual review item by ID")
    public ResponseEntity<ManualReviewResponse> getReview(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getReview(id));
    }

    @PatchMapping("/{id}/decision")
    @Operation(summary = "Resolve manual review item", description = "Submit final approval, rejection, or escalation")
    public ResponseEntity<ManualReviewResponse> resolveReview(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("Manager {} resolving review item {} to {}", userDetails.getId(), id, request.getStatus());
        return ResponseEntity.ok(reviewService.resolveReview(id, request, userDetails.getId()));
    }
}

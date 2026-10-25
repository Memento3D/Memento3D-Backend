package com.memento3d.operations.reviewqueue.application.service.impl;

import com.memento3d.operations.reviewqueue.api.request.ResolveReviewRequest;
import com.memento3d.operations.reviewqueue.api.response.ManualReviewResponse;
import com.memento3d.operations.reviewqueue.application.service.ManualReviewService;
import com.memento3d.operations.reviewqueue.domain.model.ManualReviewItem;
import com.memento3d.operations.reviewqueue.domain.model.ReviewPriority;
import com.memento3d.operations.reviewqueue.domain.model.ReviewStatus;
import com.memento3d.operations.reviewqueue.domain.model.ReviewType;
import com.memento3d.operations.reviewqueue.infrastructure.persistence.repository.ManualReviewItemRepository;
import com.memento3d.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualReviewServiceImpl implements ManualReviewService {

    private final ManualReviewItemRepository repository;

    @Override
    @Transactional
    public ManualReviewItem enqueueReview(
            ReviewType reviewType,
            ReviewPriority priority,
            UUID referenceId,
            String referenceCode,
            UUID userId,
            BigDecimal confidenceScore,
            String reasonCode,
            String reasonDetails,
            String suggestedAction
    ) {
        log.warn("Enqueuing Manual Review item: type={}, referenceId={}, reason={}", reviewType, referenceId, reasonCode);

        // Deduplicate active pending review for the same reference
        Optional<ManualReviewItem> existing = repository.findFirstByReferenceIdAndStatus(referenceId, ReviewStatus.PENDING);
        if (existing.isPresent()) {
            log.info("Pending manual review already exists for reference ID: {}", referenceId);
            return existing.get();
        }

        ManualReviewItem item = ManualReviewItem.builder()
                .reviewType(reviewType)
                .priority(priority != null ? priority : ReviewPriority.MEDIUM)
                .status(ReviewStatus.PENDING)
                .referenceId(referenceId)
                .referenceCode(referenceCode)
                .userId(userId)
                .confidenceScore(confidenceScore)
                .reasonCode(reasonCode)
                .reasonDetails(reasonDetails)
                .suggestedAction(suggestedAction)
                .build();

        return repository.save(item);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ManualReviewResponse> listReviews(
            ReviewStatus status,
            ReviewType reviewType,
            ReviewPriority priority,
            int page,
            int limit
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, limit), 100));
        return repository.searchReviews(status, reviewType, priority, pageRequest)
                .map(ManualReviewResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ManualReviewResponse getReview(UUID id) {
        ManualReviewItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual review item not found with ID: " + id));
        return ManualReviewResponse.fromEntity(item);
    }

    @Override
    @Transactional
    public ManualReviewResponse resolveReview(UUID id, ResolveReviewRequest request, UUID reviewerId) {
        ManualReviewItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual review item not found with ID: " + id));

        log.info("Resolving manual review item {} to status {} by reviewer {}", id, request.getStatus(), reviewerId);

        item.setStatus(request.getStatus());
        item.setReviewerNotes(request.getReviewerNotes());
        item.setAssignedTo(reviewerId);
        item.setResolvedAt(Instant.now());

        ManualReviewItem saved = repository.save(item);
        return ManualReviewResponse.fromEntity(saved);
    }
}

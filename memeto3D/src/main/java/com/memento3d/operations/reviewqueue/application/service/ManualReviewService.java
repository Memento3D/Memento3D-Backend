package com.memento3d.operations.reviewqueue.application.service;

import com.memento3d.operations.reviewqueue.api.request.ResolveReviewRequest;
import com.memento3d.operations.reviewqueue.api.response.ManualReviewResponse;
import com.memento3d.operations.reviewqueue.domain.model.ManualReviewItem;
import com.memento3d.operations.reviewqueue.domain.model.ReviewPriority;
import com.memento3d.operations.reviewqueue.domain.model.ReviewStatus;
import com.memento3d.operations.reviewqueue.domain.model.ReviewType;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.UUID;

public interface ManualReviewService {

    ManualReviewItem enqueueReview(
            ReviewType reviewType,
            ReviewPriority priority,
            UUID referenceId,
            String referenceCode,
            UUID userId,
            BigDecimal confidenceScore,
            String reasonCode,
            String reasonDetails,
            String suggestedAction
    );

    Page<ManualReviewResponse> listReviews(
            ReviewStatus status,
            ReviewType reviewType,
            ReviewPriority priority,
            int page,
            int limit
    );

    ManualReviewResponse getReview(UUID id);

    ManualReviewResponse resolveReview(UUID id, ResolveReviewRequest request, UUID reviewerId);
}

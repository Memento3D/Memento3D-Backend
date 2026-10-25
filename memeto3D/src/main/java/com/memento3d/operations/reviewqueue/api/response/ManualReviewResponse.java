package com.memento3d.operations.reviewqueue.api.response;

import com.memento3d.operations.reviewqueue.domain.model.ManualReviewItem;
import com.memento3d.operations.reviewqueue.domain.model.ReviewPriority;
import com.memento3d.operations.reviewqueue.domain.model.ReviewStatus;
import com.memento3d.operations.reviewqueue.domain.model.ReviewType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Manual Review Item Details")
public class ManualReviewResponse {

    private UUID id;
    private ReviewType reviewType;
    private ReviewPriority priority;
    private ReviewStatus status;
    private UUID referenceId;
    private String referenceCode;
    private UUID userId;
    private BigDecimal confidenceScore;
    private String reasonCode;
    private String reasonDetails;
    private String suggestedAction;
    private UUID assignedTo;
    private String reviewerNotes;
    private Instant resolvedAt;
    private Instant createdAt;

    public static ManualReviewResponse fromEntity(ManualReviewItem entity) {
        if (entity == null) return null;
        return ManualReviewResponse.builder()
                .id(entity.getId())
                .reviewType(entity.getReviewType())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .referenceId(entity.getReferenceId())
                .referenceCode(entity.getReferenceCode())
                .userId(entity.getUserId())
                .confidenceScore(entity.getConfidenceScore())
                .reasonCode(entity.getReasonCode())
                .reasonDetails(entity.getReasonDetails())
                .suggestedAction(entity.getSuggestedAction())
                .assignedTo(entity.getAssignedTo())
                .reviewerNotes(entity.getReviewerNotes())
                .resolvedAt(entity.getResolvedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

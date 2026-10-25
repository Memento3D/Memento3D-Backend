package com.memento3d.operations.reviewqueue.api.request;

import com.memento3d.operations.reviewqueue.domain.model.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Decision payload for Manual Review Item")
public class ResolveReviewRequest {

    @NotNull(message = "Decision status is required")
    @Schema(description = "Final decision: APPROVED, REJECTED, or ESCALATED", example = "APPROVED")
    private ReviewStatus status;

    @Schema(description = "Notes explaining rationale behind the manual decision", example = "Verified lighting is acceptable and geometry is clean.")
    private String reviewerNotes;
}

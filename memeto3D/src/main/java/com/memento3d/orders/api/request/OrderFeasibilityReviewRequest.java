package com.memento3d.orders.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderFeasibilityReviewRequest {

    @NotBlank(message = "Decision is required: FEASIBLE, REVISION_REQUESTED, REJECTED")
    private String decision;

    private String status;

    private BigDecimal finalQuotedPrice;

    private BigDecimal finalPrice;

    private Integer estimatedDays;

    private String notes;

    private String revisionGlbUrl;

    public String getEffectiveDecision() {
        if (decision != null && !decision.isBlank()) {
            return decision;
        }
        return status != null ? status : "FEASIBLE";
    }

    public BigDecimal getEffectiveFinalPrice() {
        if (finalQuotedPrice != null) {
            return finalQuotedPrice;
        }
        return finalPrice;
    }
}

package com.memento3d.models.api.request;

import com.memento3d.identity.domain.model.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request DTO for creating or updating a model 3D quality rating")
public class CreateModelRatingRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Schema(description = "Rating score from 1 to 5 stars", example = "5", minimum = "1", maximum = "5")
    private Integer rating;

    @Schema(description = "Optional user review comment/feedback", example = "Great 3D reconstruction quality!")
    private String comment;
}

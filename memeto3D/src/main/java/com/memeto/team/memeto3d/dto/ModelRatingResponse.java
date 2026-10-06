package com.memeto.team.memeto3d.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response DTO for model 3D quality rating details")
public class ModelRatingResponse {

    @Schema(description = "Rating record UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Scan session UUID", example = "123e4567-e89b-12d3-a456-426614174001")
    private UUID scanId;

    @Schema(description = "User UUID (null if guest/public rating)", example = "123e4567-e89b-12d3-a456-426614174002", nullable = true)
    private UUID userId;

    @Schema(description = "Rating score (1 to 5)", example = "5")
    private Integer rating;

    @Schema(description = "User review comment", example = "Great 3D reconstruction quality!")
    private String comment;

    @Schema(description = "Rating submission timestamp")
    private Instant createdAt;
}

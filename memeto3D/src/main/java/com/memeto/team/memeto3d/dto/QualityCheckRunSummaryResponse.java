package com.memeto.team.memeto3d.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.memeto.team.memeto3d.entity.QualityCheckRun;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@Schema(description = "Summary of a quality check run")
public class QualityCheckRunSummaryResponse {

    @JsonProperty("run_number")
    @Schema(description = "The sequential run number of this quality check", example = "1")
    private Integer runNumber;

    @Schema(description = "Status of the quality check run", example = "completed")
    private String status;

    @JsonProperty("overall_score")
    @Schema(description = "Overall quality score (0-100)", example = "85.5")
    private BigDecimal overallScore;

    @JsonProperty("score_category")
    @Schema(description = "Category of the score (green, yellow, red)", example = "green")
    private String scoreCategory;

    @JsonProperty("created_at")
    @Schema(description = "When the run was created")
    private Instant createdAt;

    @JsonProperty("completed_at")
    @Schema(description = "When the run was completed")
    private Instant completedAt;

    public static QualityCheckRunSummaryResponse fromEntity(QualityCheckRun run) {
        return QualityCheckRunSummaryResponse.builder()
                .runNumber(run.getRunNumber())
                .status(run.getStatus())
                .overallScore(run.getOverallScore())
                .scoreCategory(run.getScoreCategory())
                .createdAt(run.getCreatedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }
}

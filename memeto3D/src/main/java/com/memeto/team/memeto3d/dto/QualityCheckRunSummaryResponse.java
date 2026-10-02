package com.memeto.team.memeto3d.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QualityCheckRunSummaryResponse {
    private Integer runNumber;
    private String status;
    private Integer overallScore;
    private String scoreCategory;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

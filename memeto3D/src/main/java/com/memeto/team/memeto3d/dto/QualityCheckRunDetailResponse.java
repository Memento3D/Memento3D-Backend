package com.memeto.team.memeto3d.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class QualityCheckRunDetailResponse {
    private UUID id;
    private UUID scanId;
    private Integer runNumber;
    private String status;
    private Integer overallScore;
    private String scoreCategory;
    private String suggestion;
    private String coverageAnalysis;
    private Integer difficultyScore;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    
    private List<ImageQualityResultShortResponse> imageResults;
}

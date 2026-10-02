package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.QualityCheckRunDetailResponse;
import com.memeto.team.memeto3d.dto.QualityCheckRunSummaryResponse;
import com.memeto.team.memeto3d.entity.Role;

import java.util.List;
import java.util.UUID;

public interface QualityCheckRunService {
    List<QualityCheckRunSummaryResponse> getQualityCheckRuns(UUID scanId, UUID userId, Role role);
    QualityCheckRunDetailResponse getQualityCheckRun(UUID scanId, UUID runId, UUID userId, Role role);
}

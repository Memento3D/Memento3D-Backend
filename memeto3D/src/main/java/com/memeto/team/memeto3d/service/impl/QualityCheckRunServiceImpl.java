package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.ImageQualityResultShortResponse;
import com.memeto.team.memeto3d.dto.QualityCheckRunDetailResponse;
import com.memeto.team.memeto3d.dto.QualityCheckRunSummaryResponse;
import com.memeto.team.memeto3d.entity.QualityCheckRun;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.QualityCheckRunRepository;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.service.QualityCheckRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QualityCheckRunServiceImpl implements QualityCheckRunService {

    private final QualityCheckRunRepository qualityCheckRunRepository;
    private final ScanRepository scanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<QualityCheckRunSummaryResponse> getQualityCheckRuns(UUID scanId, UUID userId, Role role) {
        verifyScanAccess(scanId, userId, role);

        List<QualityCheckRun> runs = qualityCheckRunRepository.findByScanIdOrderByRunNumberDesc(scanId);

        return runs.stream().map(run -> QualityCheckRunSummaryResponse.builder()
                .runNumber(run.getRunNumber())
                .status(run.getStatus())
                .overallScore(run.getOverallScore())
                .scoreCategory(run.getScoreCategory())
                .createdAt(run.getCreatedAt())
                .completedAt(run.getCompletedAt())
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QualityCheckRunDetailResponse getQualityCheckRun(UUID scanId, UUID runId, UUID userId, Role role) {
        verifyScanAccess(scanId, userId, role);

        QualityCheckRun run = qualityCheckRunRepository.findByIdAndScanId(runId, scanId)
                .orElseThrow(() -> new ResourceNotFoundException("QualityCheckRun not found or doesn't belong to scan"));

        List<ImageQualityResultShortResponse> imageResults = run.getImageQualityResults().stream()
                .map(ir -> ImageQualityResultShortResponse.builder()
                        .imageId(ir.getScanImage().getId())
                        .status(ir.getStatus())
                        .build())
                .collect(Collectors.toList());

        return QualityCheckRunDetailResponse.builder()
                .id(run.getId())
                .scanId(run.getScan().getId())
                .runNumber(run.getRunNumber())
                .status(run.getStatus())
                .overallScore(run.getOverallScore())
                .scoreCategory(run.getScoreCategory())
                .suggestion(run.getSuggestion())
                .coverageAnalysis(run.getCoverageAnalysis())
                .difficultyScore(run.getDifficultyScore())
                .createdAt(run.getCreatedAt())
                .completedAt(run.getCompletedAt())
                .imageResults(imageResults)
                .build();
    }

    private Scan verifyScanAccess(UUID scanId, UUID userId, Role role) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan not found"));
        
        if (role != Role.ROLE_ADMIN && role != Role.ROLE_OPERATOR && !scan.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Scan not found"); // Hide existence
        }
        return scan;
    }
}

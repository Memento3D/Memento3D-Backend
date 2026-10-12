package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.AiJobResponse;
import com.memeto.team.memeto3d.dto.AiSubmitJobRequest;

import java.util.UUID;

public interface AiGenerationClient {

    void checkHealth();

    AiJobResponse submitJob(AiSubmitJobRequest request);

    AiJobResponse getJob(UUID jobId);

    byte[] downloadGlb(UUID jobId, UUID attemptId);
}
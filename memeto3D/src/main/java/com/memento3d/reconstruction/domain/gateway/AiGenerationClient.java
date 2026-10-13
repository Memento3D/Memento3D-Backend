package com.memento3d.reconstruction.domain.gateway;

import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;

import java.util.UUID;

public interface AiGenerationClient {

    void checkHealth();

    AiJobResponse submitJob(AiSubmitJobRequest request);

    AiJobResponse getJob(UUID jobId);

    byte[] downloadGlb(UUID jobId, UUID attemptId);
}
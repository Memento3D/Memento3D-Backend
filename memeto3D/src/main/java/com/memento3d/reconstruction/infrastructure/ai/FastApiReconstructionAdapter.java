package com.memento3d.reconstruction.infrastructure.ai;

import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.domain.port.ReconstructionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("fastApiReconstructionAdapter")
@RequiredArgsConstructor
@Slf4j
public class FastApiReconstructionAdapter implements ReconstructionProvider {

    private final AiGenerationClient aiGenerationClient;

    @Override
    public String getProviderCode() {
        return "FASTAPI";
    }

    @Override
    public int getMinImages() {
        return 1;
    }

    @Override
    public int getMaxImages() {
        return 50;
    }

    @Override
    public AiJobResponse submit(AiSubmitJobRequest request) {
        log.info("Submitting reconstruction job via FASTAPI adapter for job: {}", request.jobId());
        return aiGenerationClient.submitJob(request);
    }

    @Override
    public AiJobResponse getStatus(UUID jobId, String externalJobId) {
        return aiGenerationClient.getJob(jobId);
    }

    @Override
    public byte[] getResultGlb(UUID jobId, UUID attemptId, String externalJobId) {
        return aiGenerationClient.downloadGlb(jobId, attemptId);
    }

    @Override
    public boolean cancel(UUID jobId, String externalJobId) {
        log.info("Cancel request for FASTAPI job ID: {}", jobId);
        return false;
    }
}

package com.memento3d.reconstruction.infrastructure.ai;

import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.domain.port.ReconstructionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("httpAiReconstructionProvider")
@RequiredArgsConstructor
@Slf4j
public class HttpAiReconstructionProvider implements ReconstructionProvider {

    private final AiGenerationClient aiGenerationClient;

    @Override
    public String getProviderCode() {
        return "HTTP_AI";
    }

    @Override
    public int getMinImages() {
        return 3;
    }

    @Override
    public int getMaxImages() {
        return 4;
    }

    @Override
    public AiJobResponse submit(AiSubmitJobRequest request) {
        log.info("Submitting reconstruction job via HTTP_AI provider for job: {}", request.jobId());
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
        log.info("Cancel request for HTTP_AI job ID: {}", jobId);
        return false;
    }
}

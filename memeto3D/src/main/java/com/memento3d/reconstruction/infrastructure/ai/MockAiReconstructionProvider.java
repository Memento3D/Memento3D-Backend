package com.memento3d.reconstruction.infrastructure.ai;

import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;
import com.memento3d.reconstruction.domain.port.ReconstructionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component("mockAiReconstructionProvider")
@Slf4j
public class MockAiReconstructionProvider implements ReconstructionProvider {

    @Override
    public String getProviderCode() {
        return "MOCK_AI";
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
        log.info("Submitting reconstruction job via MOCK_AI provider for scan: {}", request.getScanId());
        return new AiJobResponse(
                request.getJobId(),
                "running",
                "meshing",
                "Mock AI job accepted and in progress",
                10,
                Instant.now(),
                null
        );
    }

    @Override
    public AiJobResponse getStatus(UUID jobId, String externalJobId) {
        return new AiJobResponse(
                jobId,
                "done",
                "texturing",
                "Mock AI reconstruction completed successfully",
                100,
                Instant.now().minusSeconds(30),
                Instant.now()
        );
    }

    @Override
    public byte[] getResultGlb(UUID jobId, UUID attemptId, String externalJobId) {
        // Return dummy bytes or load asset if needed
        return new byte[]{0x67, 0x6c, 0x54, 0x46}; // glTF binary magic bytes header
    }

    @Override
    public boolean cancel(UUID jobId, String externalJobId) {
        log.info("Cancelled MOCK_AI job ID: {}", jobId);
        return true;
    }
}

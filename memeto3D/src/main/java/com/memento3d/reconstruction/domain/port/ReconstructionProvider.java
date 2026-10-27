package com.memento3d.reconstruction.domain.port;

import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.dto.AiSubmitJobRequest;

import java.util.UUID;

/**
 * Port interface for 3D reconstruction AI providers.
 * Isolates core job state machine and business logic from AI cluster protocols.
 */
public interface ReconstructionProvider {

    String getProviderCode();

    int getMinImages();

    int getMaxImages();

    AiJobResponse submit(AiSubmitJobRequest request);

    AiJobResponse getStatus(UUID jobId, String externalJobId);

    byte[] getResultGlb(UUID jobId, UUID attemptId, String externalJobId);

    default boolean cancel(UUID jobId, String externalJobId) {
        return false;
    }
}

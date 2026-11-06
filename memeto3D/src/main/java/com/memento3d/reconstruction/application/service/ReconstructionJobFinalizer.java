package com.memento3d.reconstruction.application.service;

import java.util.UUID;

public interface ReconstructionJobFinalizer {

    void finish(UUID jobId, UUID attemptId);
    default void finish(UUID jobId,UUID attemptId,com.memento3d.reconstruction.application.dto.AiJobResponse status) {
        finish(jobId,attemptId);
    }
}
package com.memeto.team.memeto3d.service;

import java.util.UUID;

public interface ReconstructionJobFinalizer {

    void finish(UUID jobId, UUID attemptId);
}
package com.memento3d.reconstruction.application.service;

import com.memento3d.reconstruction.api.request.CreateReconstructionJobRequest;
import com.memento3d.reconstruction.api.response.ReconstructionJobResponse;
import com.memento3d.identity.domain.model.Role;

import java.util.UUID;

public interface ReconstructionJobService {

    ReconstructionJobResponse submitJob(
            UUID scanId,
            CreateReconstructionJobRequest request,
            UUID currentUserId,
            Role currentUserRole
    );

    ReconstructionJobResponse getLatestJob(
            UUID scanId,
            UUID currentUserId,
            Role currentUserRole
    );

    ReconstructionJobResponse getJobById(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    );

    java.util.List<com.memento3d.models.api.response.ModelOutputResponse> getJobOutputs(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    );

    ReconstructionJobResponse cancelJob(
            UUID jobId,
            UUID currentUserId,
            Role currentUserRole
    );
}
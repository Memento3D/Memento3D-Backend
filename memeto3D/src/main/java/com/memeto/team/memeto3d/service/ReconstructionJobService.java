package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateReconstructionJobRequest;
import com.memeto.team.memeto3d.dto.ReconstructionJobResponse;
import com.memeto.team.memeto3d.entity.Role;

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
}
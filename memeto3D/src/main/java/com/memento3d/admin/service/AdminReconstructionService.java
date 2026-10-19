package com.memento3d.admin.service;

import com.memento3d.reconstruction.api.response.ReconstructionJobResponse;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminReconstructionService {
    Page<ReconstructionJobResponse> listJobs(ReconstructionJobStatus status, ReconstructionTier tier, Pageable pageable);
    ReconstructionJobResponse getJobDetail(UUID id);
    ReconstructionJobResponse retryFailedJob(UUID id, UUID adminActorId);
}

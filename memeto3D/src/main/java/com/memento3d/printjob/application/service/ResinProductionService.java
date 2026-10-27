package com.memento3d.printjob.application.service;

import com.memento3d.printjob.api.request.*;
import com.memento3d.printjob.api.response.ResinProductionJobResponse;

import java.util.List;
import java.util.UUID;

public interface ResinProductionService {

    ResinProductionJobResponse prepareProductionJob(UUID orderItemId, PrepareResinProductionRequest request, UUID operatorId);

    ResinProductionJobResponse recordSlicing(UUID printJobId, RecordSlicingRequest request, UUID operatorId);

    ResinProductionJobResponse confirmHandoff(UUID printJobId, ConfirmHandoffRequest request, UUID operatorId);

    ResinProductionJobResponse confirmStartPrinting(UUID printJobId, UUID operatorId);

    ResinProductionJobResponse updatePostProcessing(UUID printJobId, PostProcessingChecklistRequest request, UUID operatorId);

    ResinProductionJobResponse recordQcResult(UUID printJobId, RecordQcResultRequest request, UUID inspectorId);

    ResinProductionJobResponse createReprint(UUID failedPrintJobId, UUID operatorId);

    ResinProductionJobResponse getJobDetails(UUID printJobId);

    List<ResinProductionJobResponse> listResinJobs(int page, int limit);
}

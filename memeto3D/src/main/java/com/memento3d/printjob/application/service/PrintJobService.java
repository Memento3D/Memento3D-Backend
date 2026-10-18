package com.memento3d.printjob.application.service;

import com.memento3d.printjob.api.request.ConsumptionReconciliationRequest;
import com.memento3d.printjob.api.request.CreatePrintJobRequest;
import com.memento3d.printjob.api.request.MaterialIssueRequest;
import com.memento3d.printjob.api.request.ReserveStockRequest;
import com.memento3d.printjob.api.response.PrintJobConsumptionResponse;
import com.memento3d.printjob.api.response.PrintJobResponse;
import com.memento3d.printjob.api.response.StockReservationResponse;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PrintJobService {

    PrintJobResponse createPrintJob(CreatePrintJobRequest request, UUID actorId);

    Page<PrintJobResponse> listPrintJobs(PrintJobStatus status, UUID printerId, UUID materialId, Pageable pageable);

    PrintJobResponse getPrintJob(UUID id);

    StockReservationResponse reserveStock(UUID printJobId, ReserveStockRequest request, UUID actorId);

    StockReservationResponse getReservation(UUID printJobId);

    void releaseReservation(UUID printJobId, UUID actorId);

    PrintJobConsumptionResponse issueMaterial(UUID printJobId, MaterialIssueRequest request, UUID actorId);

    PrintJobConsumptionResponse reconcileConsumption(UUID printJobId, ConsumptionReconciliationRequest request, UUID actorId);

    PrintJobConsumptionResponse getConsumption(UUID printJobId);

    PrintJobResponse startPrint(UUID printJobId);
}

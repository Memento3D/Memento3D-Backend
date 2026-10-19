package com.memento3d.quality.application;

import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.quality.domain.model.PhysicalQualityCheck;
import com.memento3d.quality.infrastructure.persistence.repository.PhysicalQualityCheckRepository;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhysicalQualityServiceImpl implements PhysicalQualityService {

    private final PhysicalQualityCheckRepository physicalQualityCheckRepository;
    private final PrintJobRepository printJobRepository;
    private final OutboxPublisherService outboxPublisherService;

    @Override
    @Transactional
    public PhysicalQualityCheck recordQcCheck(
            UUID printJobId,
            UUID inspectorId,
            boolean modelAccuracyPass,
            boolean surfaceQualityPass,
            boolean dimensionsPass,
            boolean colorFinishingPass,
            boolean structuralIntegrityPass,
            boolean packagingPass,
            String failureReasonCode,
            String evidenceUrls,
            String notes,
            boolean requestReprint) {

        PrintJob printJob = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new IllegalArgumentException("Print job not found: " + printJobId));

        boolean allPassed = modelAccuracyPass && surfaceQualityPass && dimensionsPass
                && colorFinishingPass && structuralIntegrityPass && packagingPass;

        String status = allPassed ? "PASS" : "FAIL";

        PhysicalQualityCheck check = PhysicalQualityCheck.builder()
                .printJobId(printJobId)
                .orderItemId(printJob.getOrderItemId())
                .inspectorId(inspectorId)
                .status(status)
                .modelAccuracyPass(modelAccuracyPass)
                .surfaceQualityPass(surfaceQualityPass)
                .dimensionsPass(dimensionsPass)
                .colorFinishingPass(colorFinishingPass)
                .structuralIntegrityPass(structuralIntegrityPass)
                .packagingPass(packagingPass)
                .failureReasonCode(failureReasonCode)
                .evidenceUrls(evidenceUrls)
                .notes(notes)
                .reprintRequired(requestReprint || !allPassed)
                .build();

        PhysicalQualityCheck saved = physicalQualityCheckRepository.save(check);

        if ("PASS".equals(status)) {
            printJob.setStatus(PrintJobStatus.COMPLETED);
            printJobRepository.save(printJob);

            outboxPublisherService.publish(
                    "PhysicalQualityCheck",
                    saved.getId().toString(),
                    "QC_PASSED",
                    Map.of(
                            "printJobId", printJobId,
                            "orderId", printJob.getOrderId() != null ? printJob.getOrderId().toString() : "",
                            "inspectorId", inspectorId
                    )
            );
        } else {
            printJob.setStatus(PrintJobStatus.FAILED);
            printJob.setFailureReason("QC FAIL: " + failureReasonCode + " - " + notes);
            printJobRepository.save(printJob);

            outboxPublisherService.publish(
                    "PhysicalQualityCheck",
                    saved.getId().toString(),
                    "QC_FAILED",
                    Map.of(
                            "printJobId", printJobId,
                            "failureReasonCode", failureReasonCode != null ? failureReasonCode : "UNSPECIFIED",
                            "reprintRequired", check.isReprintRequired()
                    )
            );

            if (check.isReprintRequired()) {
                PrintJob reprintJob = PrintJob.builder()
                        .jobNumber(printJob.getJobNumber() + "-R" + (System.currentTimeMillis() % 10000))
                        .orderId(printJob.getOrderId())
                        .orderItemId(printJob.getOrderItemId())
                        .modelVersionId(printJob.getModelVersionId())
                        .printer(printJob.getPrinter())
                        .material(printJob.getMaterial())
                        .estimatedGram(printJob.getEstimatedGram())
                        .estimatedDurationSeconds(printJob.getEstimatedDurationSeconds())
                        .reprintOfJobId(printJob.getId())
                        .status(PrintJobStatus.CREATED)
                        .createdBy(inspectorId)
                        .build();

                PrintJob savedReprint = printJobRepository.save(reprintJob);
                log.info("Auto-created reprint job {} for failed print job {}", savedReprint.getJobNumber(), printJobId);
            }
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PhysicalQualityCheck> getQcHistory(UUID printJobId) {
        return physicalQualityCheckRepository.findByPrintJobIdOrderByCreatedAtDesc(printJobId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPrintJobPassedQc(UUID printJobId) {
        return physicalQualityCheckRepository.findFirstByPrintJobIdOrderByCreatedAtDesc(printJobId)
                .map(qc -> "PASS".equalsIgnoreCase(qc.getStatus()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public void validatePrintJobPassedForShipment(UUID orderId) {
        List<PrintJob> jobs = printJobRepository.findAll().stream()
                .filter(j -> orderId.equals(j.getOrderId()))
                .toList();

        if (jobs.isEmpty()) {
            return; // Digital or no print job
        }

        boolean hasActivePassed = jobs.stream().anyMatch(j -> isPrintJobPassedQc(j.getId()));
        if (!hasActivePassed) {
            throw new IllegalStateException("Order " + orderId + " cannot be shipped: No print job has passed physical QC");
        }
    }
}

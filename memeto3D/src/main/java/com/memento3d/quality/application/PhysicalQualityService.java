package com.memento3d.quality.application;

import com.memento3d.quality.domain.model.PhysicalQualityCheck;

import java.util.List;
import java.util.UUID;

public interface PhysicalQualityService {

    PhysicalQualityCheck recordQcCheck(
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
            boolean requestReprint
    );

    List<PhysicalQualityCheck> getQcHistory(UUID printJobId);

    boolean isPrintJobPassedQc(UUID printJobId);

    void validatePrintJobPassedForShipment(UUID orderId);
}

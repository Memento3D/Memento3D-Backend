package com.memento3d.models.application.service.impl;

import com.memento3d.models.api.response.ModelOutputResponse;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanStatus;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.models.application.service.ModelOutputService;
import com.memento3d.scans.application.service.ScanAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModelOutputServiceImpl implements ModelOutputService {

    private final ScanRepository scanRepository;
    private final ModelOutputRepository outputRepository;
    private final ScanAccessGuard scanAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public List<ModelOutputResponse> getScanOutputs(
            UUID scanId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        Scan scan = scanRepository.findByIdAndDeletedAtIsNull(scanId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Scan not found with ID: " + scanId
                ));

        if (scan.getStatus() == ScanStatus.deleted) {
            throw new ResourceNotFoundException(
                    "Scan not found with ID: " + scanId
            );
        }

        scanAccessGuard.requireAccess(
                scan,
                currentUserId,
                currentUserRole
        );

        return outputRepository
                .findByJob_Scan_IdOrderByCreatedAtDesc(scanId)
                .stream()
                .map(ModelOutputResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ModelOutputResponse getModelOutputById(
            UUID modelId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        com.memento3d.models.domain.model.ModelOutput output = outputRepository.findById(modelId)
                .orElseThrow(() -> new ResourceNotFoundException("Model output not found with ID: " + modelId));

        Scan scan = output.getJob().getScan();
        scanAccessGuard.requireAccess(scan, currentUserId, currentUserRole);

        return ModelOutputResponse.fromEntity(output);
    }

    @Override
    @Transactional
    public void deleteModelOutput(
            UUID modelId,
            UUID currentUserId,
            Role currentUserRole
    ) {
        com.memento3d.models.domain.model.ModelOutput output = outputRepository.findById(modelId)
                .orElseThrow(() -> new ResourceNotFoundException("Model output not found with ID: " + modelId));

        Scan scan = output.getJob().getScan();
        scanAccessGuard.requireAccess(scan, currentUserId, currentUserRole);

        outputRepository.delete(output);
    }
}
package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.ModelOutputResponse;
import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.ScanStatus;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.ModelOutputRepository;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.service.ModelOutputService;
import com.memeto.team.memeto3d.service.ScanAccessGuard;
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
}
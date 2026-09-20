package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanResponse;
import com.memeto.team.memeto3d.entity.Role;

import java.util.UUID;

public interface ScanService {

    ScanResponse createScan(CreateScanRequest request, UUID userId);

    ScanResponse getScanById(UUID id, UUID currentUserId, Role currentUserRole);
}

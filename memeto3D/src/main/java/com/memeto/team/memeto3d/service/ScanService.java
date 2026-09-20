package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateScanRequest;
import com.memeto.team.memeto3d.dto.ScanListResponse;
import com.memeto.team.memeto3d.dto.ScanResponse;

import java.util.UUID;

public interface ScanService {

    ScanResponse createScan(CreateScanRequest request, UUID userId);

    ScanListResponse getScans(UUID userId, String status, int page, int limit, String sort);
}

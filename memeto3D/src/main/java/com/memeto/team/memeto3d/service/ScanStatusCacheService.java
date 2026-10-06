package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.ScanStatusResponse;

import java.util.Optional;
import java.util.UUID;

public interface ScanStatusCacheService {
    Optional<String> getScanStatus(UUID scanId);
    void cacheScanStatus(UUID scanId, String status);
    void evictScanStatus(UUID scanId);

    Optional<Integer> getScanProgress(UUID scanId);
    void cacheScanProgress(UUID scanId, int progress);

    Optional<String> getScanMessage(UUID scanId);
    void cacheScanMessage(UUID scanId, String message);

    Optional<ScanStatusResponse> getScanStatusDetails(UUID scanId);
    void cacheScanStatusDetails(UUID scanId, String status, Integer progress, String message);
}

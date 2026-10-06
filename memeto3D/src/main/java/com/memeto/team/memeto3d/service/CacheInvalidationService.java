package com.memeto.team.memeto3d.service;

import java.util.UUID;

public interface CacheInvalidationService {
    void invalidateUserCache(UUID userId);
    void invalidateScanCache(UUID scanId);
    void invalidateAllUserCaches();
    void invalidateAllScanCaches();
}

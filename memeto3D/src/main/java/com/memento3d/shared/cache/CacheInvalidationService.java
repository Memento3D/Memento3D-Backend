package com.memento3d.shared.cache;

import java.util.UUID;

public interface CacheInvalidationService {
    void invalidateUserCache(UUID userId);
    void invalidateScanCache(UUID scanId);
    void invalidateAllUserCaches();
    void invalidateAllScanCaches();
}

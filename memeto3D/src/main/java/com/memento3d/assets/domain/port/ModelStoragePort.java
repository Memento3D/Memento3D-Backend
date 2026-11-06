package com.memento3d.assets.domain.port;

import java.io.IOException;
import java.util.UUID;

/** Model artifacts are separate from image/avatar storage. */
public interface ModelStoragePort {
    record StoredModel(String provider, String objectKey, String assetId, String location, long sizeBytes) {}
    String provider();
    StoredModel uploadGlb(byte[] bytes, UUID userId, UUID scanId, UUID jobId, UUID attemptId) throws IOException;
    default StoredModel uploadGlbArtifact(byte[] bytes,UUID userId,UUID scanId,UUID jobId,UUID attemptId,String artifactName) throws IOException {
        throw new UnsupportedOperationException("Storage does not support named model artifacts");
    }
    String downloadUrl(String objectKey, String location);
    default long urlTtlSeconds() { return 3600; }
}

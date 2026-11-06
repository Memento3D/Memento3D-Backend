package com.memento3d.customization.domain.port;

/** Read-only remote verification; must not upload or mutate bucket objects. */
public interface CatalogObjectVerifier {
    void verify(String bucket,String objectKey,long sizeBytes,String sha256);
}

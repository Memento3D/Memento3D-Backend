package com.memento3d.assets.domain.port;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Port interface for decoupled media storage operations (Cloudinary, S3, Local, etc.)
 * Strictly isolates business logic from third-party storage vendor SDKs.
 */
public interface MediaStoragePort {

    String getProviderName();

    Map<String, Object> uploadImage(
            MultipartFile file,
            UUID userId,
            UUID scanId,
            UUID imageId
    ) throws IOException;

    Map<String, Object> uploadGlb(
            byte[] bytes,
            UUID userId,
            UUID scanId,
            UUID jobId,
            UUID attemptId
    ) throws IOException;

    Map<String, Object> uploadAvatar(byte[] bytes, UUID userId) throws IOException;

    void deleteAvatar(UUID userId) throws IOException;

    void deleteAsset(String publicId, String resourceType) throws IOException;

    String getAccessUrl(String publicId, String resourceType);
}

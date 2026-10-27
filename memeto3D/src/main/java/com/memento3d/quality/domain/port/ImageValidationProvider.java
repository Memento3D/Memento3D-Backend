package com.memento3d.quality.domain.port;

import java.util.UUID;

public interface ImageValidationProvider {

    String getProviderCode();

    ImageValidationResult validate(UUID scanId, UUID imageId, String imageUrl);

    record ImageValidationResult(
            boolean valid,
            String qualityStatus,
            String format,
            long fileSizeBytes,
            String message
    ) {
        public static ImageValidationResult passed(String format, long fileSizeBytes, String message) {
            return new ImageValidationResult(true, "PASSED", format, fileSizeBytes, message);
        }

        public static ImageValidationResult failed(String message) {
            return new ImageValidationResult(false, "FAILED", "unknown", 0L, message);
        }
    }
}

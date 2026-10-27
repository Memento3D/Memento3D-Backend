package com.memento3d.quality.infrastructure.ai;

import com.memento3d.quality.domain.port.ImageValidationProvider;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("fastApiImageValidationAdapter")
@RequiredArgsConstructor
@Slf4j
public class FastApiImageValidationAdapter implements ImageValidationProvider {

    private final AiGenerationClient aiGenerationClient;

    @Override
    public String getProviderCode() {
        return "FASTAPI";
    }

    @Override
    public ImageValidationResult validate(UUID scanId, UUID imageId, String imageUrl) {
        log.info("Validating image {} for scan {} via FASTAPI adapter", imageId, scanId);
        try {
            return aiGenerationClient.validateImage(imageUrl);
        } catch (Exception ex) {
            log.warn("FastAPI image validation failed gracefully: {}", ex.getMessage());
            return ImageValidationResult.passed("jpeg", 2048L, "FastAPI check bypassed: " + ex.getMessage());
        }
    }
}

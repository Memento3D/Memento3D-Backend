package com.memento3d.quality.infrastructure.ai;

import com.memento3d.quality.domain.port.ImageValidationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("mockImageValidationProvider")
@Slf4j
public class MockImageValidationProvider implements ImageValidationProvider {

    @Override
    public String getProviderCode() {
        return "MOCK";
    }

    @Override
    public ImageValidationResult validate(UUID scanId, UUID imageId, String imageUrl) {
        log.info("Mock validating image {} for scan {}", imageId, scanId);
        return ImageValidationResult.passed("jpeg", 4096L, "Image passed mock pre-validation check.");
    }
}

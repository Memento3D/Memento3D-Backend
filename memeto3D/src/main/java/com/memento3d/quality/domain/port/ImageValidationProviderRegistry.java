package com.memento3d.quality.domain.port;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ImageValidationProviderRegistry {

    private final Map<String, ImageValidationProvider> providerMap = new ConcurrentHashMap<>();
    private final String defaultProviderCode;

    public ImageValidationProviderRegistry(
            List<ImageValidationProvider> providers,
            @Value("${app.ai.image-validation-provider:FASTAPI}") String defaultProviderCode
    ) {
        this.defaultProviderCode = defaultProviderCode.toUpperCase();
        for (ImageValidationProvider provider : providers) {
            String code = provider.getProviderCode().toUpperCase();
            providerMap.put(code, provider);
            log.info("Registered Image Validation Provider: {}", code);
        }
    }

    public ImageValidationProvider getProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return getDefaultProvider();
        }
        ImageValidationProvider provider = providerMap.get(providerCode.toUpperCase());
        if (provider == null) {
            log.warn("ImageValidationProvider '{}' not found, falling back to default '{}'", providerCode, defaultProviderCode);
            return getDefaultProvider();
        }
        return provider;
    }

    public ImageValidationProvider getDefaultProvider() {
        ImageValidationProvider provider = providerMap.get(defaultProviderCode);
        if (provider == null) {
            return providerMap.values().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No ImageValidationProvider registered in system"));
        }
        return provider;
    }
}

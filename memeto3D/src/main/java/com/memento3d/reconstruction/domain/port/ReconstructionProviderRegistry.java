package com.memento3d.reconstruction.domain.port;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ReconstructionProviderRegistry {

    private final Map<String, ReconstructionProvider> providerMap = new ConcurrentHashMap<>();
    private final String defaultProviderCode;

    public ReconstructionProviderRegistry(
            List<ReconstructionProvider> providers,
            @Value("${app.ai.default-provider:HTTP_AI}") String defaultProviderCode
    ) {
        this.defaultProviderCode = defaultProviderCode.toUpperCase();
        for (ReconstructionProvider provider : providers) {
            String code = provider.getProviderCode().toUpperCase();
            providerMap.put(code, provider);
            log.info("Registered 3D Reconstruction Provider: {}", code);
        }
    }

    public ReconstructionProvider getProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return getDefaultProvider();
        }
        ReconstructionProvider provider = providerMap.get(providerCode.toUpperCase());
        if (provider == null) {
            log.warn("Reconstruction provider '{}' not found, falling back to default '{}'", providerCode, defaultProviderCode);
            return getDefaultProvider();
        }
        return provider;
    }

    public ReconstructionProvider getDefaultProvider() {
        ReconstructionProvider provider = providerMap.get(defaultProviderCode);
        if (provider == null) {
            // If default not found, pick any registered provider
            return providerMap.values().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No 3D Reconstruction Provider available in registry"));
        }
        return provider;
    }
}

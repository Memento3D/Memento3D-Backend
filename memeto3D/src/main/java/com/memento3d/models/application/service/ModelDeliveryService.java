package com.memento3d.models.application.service;

import com.memento3d.assets.domain.port.ModelStoragePort;
import com.memento3d.models.api.response.ModelOutputResponse;
import com.memento3d.models.domain.model.ModelOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelDeliveryService {
    private final ModelStoragePort storage;
    public String url(String provider,String objectKey,String location) {
        if (!"b2".equalsIgnoreCase(provider)) return location;
        if (!"b2".equals(storage.provider())) throw new IllegalStateException("B2 storage is required to access this model");
        return storage.downloadUrl(objectKey,location);
    }
    public String url(ModelOutput output) {
        return url(output.getStorageProvider(),output.getStoragePublicId(),output.getDeliveryUrl());
    }
    public ModelOutputResponse toResponse(ModelOutput output) {
        return ModelOutputResponse.fromEntity(output).toBuilder().deliveryUrl(url(output)).build();
    }
    public long urlTtlSeconds() { return storage.urlTtlSeconds(); }
}

package com.memento3d.assets.infrastructure.storage;

import com.memento3d.assets.domain.port.ModelStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.model-storage.provider", havingValue = "cloudinary", matchIfMissing = true)
public class CloudinaryModelStorage implements ModelStoragePort {
    private final CloudinaryStorageService cloudinary;
    public String provider() { return "cloudinary"; }
    public StoredModel uploadGlb(byte[] bytes, UUID user, UUID scan, UUID job, UUID attempt) throws IOException {
        Map<String,Object> result = cloudinary.uploadGlb(bytes,user,scan,job,attempt);
        Object size = result.get("bytes");
        if (!(size instanceof Number)) throw new IOException("Storage response lacks file size");
        return new StoredModel(provider(),required(result,"public_id"),required(result,"asset_id"),
                required(result,"secure_url"),((Number)size).longValue());
    }
    public String downloadUrl(String objectKey, String location) { return location; }
    private String required(Map<String,Object> result,String name) throws IOException {
        Object value=result.get(name);
        if (!(value instanceof String s) || s.isBlank()) throw new IOException("Storage response lacks "+name);
        return (String)value;
    }
}

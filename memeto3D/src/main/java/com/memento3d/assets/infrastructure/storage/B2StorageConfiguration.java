package com.memento3d.assets.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name="app.model-storage.provider",havingValue="b2")
public class B2StorageConfiguration {
    @Bean(destroyMethod="close")
    public B2ModelStorage b2ModelStorage(
            @Value("${app.model-storage.b2.endpoint}") String endpoint,
            @Value("${app.model-storage.b2.region}") String region,
            @Value("${app.model-storage.b2.bucket}") String bucket,
            @Value("${app.model-storage.b2.key-id:}") String keyId,
            @Value("${app.model-storage.b2.application-key:}") String key,
            @Value("${app.model-storage.b2.url-ttl-seconds:3600}") long ttl) {
        return B2ModelStorage.connect(endpoint,region,bucket,keyId,key,ttl);
    }
}

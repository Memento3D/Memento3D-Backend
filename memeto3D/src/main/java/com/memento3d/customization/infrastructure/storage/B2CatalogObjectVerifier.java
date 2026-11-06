package com.memento3d.customization.infrastructure.storage;

import com.memento3d.assets.infrastructure.storage.B2ModelStorage;
import com.memento3d.customization.domain.port.CatalogObjectVerifier;
import com.memento3d.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class B2CatalogObjectVerifier implements CatalogObjectVerifier {
    private final ObjectProvider<B2ModelStorage> storage;
    @Override public void verify(String bucket,String objectKey,long bytes,String sha256) {
        var b2=storage.getIfAvailable();
        if(b2==null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"B2_STORAGE_REQUIRED","B2 storage is required to verify the catalog");
        try { b2.verifyObject(bucket,objectKey,bytes,sha256); }
        catch(IllegalArgumentException | IllegalStateException ex) {
            throw new BusinessException(HttpStatus.CONFLICT,"CATALOG_OBJECT_MISMATCH","B2 catalog object does not match its pinned size/checksum");
        } catch(Exception ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"CATALOG_STORAGE_UNAVAILABLE","Cannot verify B2 catalog objects");
        }
    }
}

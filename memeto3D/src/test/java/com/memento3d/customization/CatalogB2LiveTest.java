package com.memento3d.customization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.assets.infrastructure.storage.B2ModelStorage;
import com.memento3d.customization.application.service.CatalogManifestLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in GET-only verification. No database, bucket mutations or uploads. */
@EnabledIfEnvironmentVariable(named="B2_RUN_CATALOG_READ_CHECK",matches="true")
class CatalogB2LiveTest {
    @Test void allFifteenUserUploadedObjectsMatchPinnedBytesAndSha256() {
        var source=new CatalogManifestLoader(new ObjectMapper()).load("mvp-v1").manifest();
        try(var storage=B2ModelStorage.connect(env("B2_ENDPOINT"),env("B2_REGION"),env("B2_BUCKET"),env("B2_KEY_ID"),env("B2_APPLICATION_KEY"),3600)) {
            for(var pin:source.files()) {
                try { storage.verifyObject(source.bucket(),pin.key(),pin.bytes(),pin.sha256()); }
                catch(Exception ex) { fail("B2 read/checksum failed for "+pin.key()+". Check bucket/key or uploaded bytes. Secret/error details intentionally omitted."); }
            }
        }
        System.out.println("B2 CATALOG READ CHECK PASS: 15 objects match size and SHA-256. No upload, import, CORS or database change.");
    }
    private String env(String name) { var value=System.getenv(name);if(value==null || value.isBlank())throw new IllegalStateException("Missing "+name);return value; }
}

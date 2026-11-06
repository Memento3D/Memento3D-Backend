package com.memento3d.assets.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real B2 test; creates one tiny, synthetic private GLB-shaped object. */
@EnabledIfEnvironmentVariable(named="B2_RUN_LIVE_CHECK",matches="true")
class B2LiveStorageTest {
    @Test void privateUploadAndSignedDownloadRoundTrip() throws Exception {
        try(var storage=B2ModelStorage.connect(env("B2_ENDPOINT"),env("B2_REGION"),env("B2_BUCKET"),
                env("B2_KEY_ID"),env("B2_APPLICATION_KEY"),3600)) {
            if ("true".equals(System.getenv("B2_CONFIGURE_CORS"))) {
                var origins=Arrays.stream(env("B2_CORS_ORIGINS").split(",")).map(String::trim).toList();
                storage.configureViewerCors(origins);
                System.out.println("B2 viewer CORS configured for the supplied FE origins.");
            }
            byte[] data=new byte[64];data[0]='g';data[1]='l';data[2]='T';data[3]='F';
            UUID id=UUID.randomUUID();
            var result=storage.uploadGlb(data,id,id,id,id);
            String signed=storage.downloadUrl(result.objectKey(),result.location());
            var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
                    .send(HttpRequest.newBuilder(URI.create(signed)).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,response.statusCode(),"Signed B2 download must succeed");
            assertArrayEquals(data,response.body());
            var unsigned=HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create(System.getenv("B2_ENDPOINT")+"/"+System.getenv("B2_BUCKET")+"/"+result.objectKey()))
                    .timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.discarding());
            assertTrue(unsigned.statusCode()==401 || unsigned.statusCode()==403,"Bucket must remain private");
            System.out.println("B2 LIVE CHECK PASS: private upload, byte-identical signed download, anonymous download denied. No key or signed URL printed.");
        }
    }
    private String env(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank())throw new IllegalStateException("Missing "+name);
        return value;
    }
}

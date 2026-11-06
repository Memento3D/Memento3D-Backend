package com.memento3d.assets.infrastructure.storage;

import com.memento3d.models.application.service.ModelDeliveryService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class B2ModelStorageTest {
    private final UUID user=UUID.randomUUID(),scan=UUID.randomUUID(),job=UUID.randomUUID(),attempt=UUID.randomUUID();
    private final String bucket="memento3d-models-tiendon";
    private byte[] glb(int length) {
        byte[] bytes=new byte[length];bytes[0]='g';bytes[1]='l';bytes[2]='T';bytes[3]='F';return bytes;
    }
    private HeadObjectResponse head(byte[] bytes) throws Exception {
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return HeadObjectResponse.builder().contentLength((long)bytes.length).eTag("test-etag").metadata(Map.of("sha256",hash)).build();
    }
    @Test void uploadAboveCloudinaryLimitUsesPrivateB2AndStableReference() throws Exception {
        S3Client client=mock(S3Client.class);S3Presigner signer=mock(S3Presigner.class);
        byte[] bytes=glb(26*1024*1024);
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build()).thenReturn(head(bytes));
        var storage=new B2ModelStorage(client,signer,bucket,Duration.ofHours(1));
        var result=storage.uploadGlb(bytes,user,scan,job,attempt);
        assertEquals("b2",result.provider());assertEquals(bytes.length,result.sizeBytes());
        assertTrue(result.location().startsWith("b2://"+bucket+"/"));assertFalse(result.location().contains("Signature"));
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        var body=org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(),body.capture());
        assertEquals("model/gltf-binary",request.getValue().contentType());
        assertEquals(bucket,request.getValue().bucket());assertNull(request.getValue().acl());
        assertArrayEquals(bytes,body.getValue().contentStreamProvider().newStream().readAllBytes());
        verifyNoInteractions(signer);
    }
    @Test void retryDoesNotCreateAnotherObjectVersion() throws Exception {
        S3Client client=mock(S3Client.class);byte[] bytes=glb(20);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(bytes));
        var storage=new B2ModelStorage(client,mock(S3Presigner.class),bucket,Duration.ofHours(1));
        storage.uploadGlb(bytes,user,scan,job,attempt);
        verify(client,never()).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test void mismatchedAttemptArtifactIsNotOverwritten() {
        S3Client client=mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(20L).metadata(Map.of("sha256","different")).build());
        var storage=new B2ModelStorage(client,mock(S3Presigner.class),bucket,Duration.ofHours(1));
        assertThrows(IllegalStateException.class,()->storage.uploadGlb(glb(20),user,scan,job,attempt));
        verify(client,never()).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test void permissionErrorIsNotTreatedAsMissingObject() {
        S3Client client=mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        var storage=new B2ModelStorage(client,mock(S3Presigner.class),bucket,Duration.ofHours(1));
        assertThrows(S3Exception.class,()->storage.uploadGlb(glb(20),user,scan,job,attempt));
        verify(client,never()).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test void presignUsesCorrectRegionAndDoesNotExposeApplicationKey() {
        try(var storage=B2ModelStorage.connect("https://s3.us-east-005.backblazeb2.com","us-east-005",bucket,"test-key-id","test-secret-only",3600)) {
            String key="models/test.glb";
            URI url=URI.create(storage.downloadUrl(key,"b2://"+bucket+"/"+key));
            assertEquals("s3.us-east-005.backblazeb2.com",url.getHost());
            assertEquals("/"+bucket+"/"+key,url.getPath());
            assertTrue(url.getQuery().contains("X-Amz-Signature="));
            assertTrue(url.getQuery().contains("us-east-005"));
            assertTrue(url.getQuery().contains("X-Amz-Expires=3600"));
            assertFalse(url.toString().contains("test-secret-only"));
        }
    }
    @Test void invalidEndpointFailsBeforeAnyNetworkCall() {
        assertThrows(IllegalArgumentException.class,()->B2ModelStorage.connect("http://localhost:9000","us-east-005",bucket,"id","key",3600));
    }
    @Test void oldCloudinaryOutputsStillReturnExistingUrl() {
        var storage=mock(com.memento3d.assets.domain.port.ModelStoragePort.class);
        var delivery=new ModelDeliveryService(storage);
        assertEquals("https://example.test/old.glb",delivery.url("cloudinary","old","https://example.test/old.glb"));
        verifyNoInteractions(storage);
    }
    @Test void corsSetupPreservesOtherRulesAndDoesNotGrantBrowserUploads() {
        var client=mock(S3Client.class);
        when(client.getBucketCors(any(GetBucketCorsRequest.class))).thenReturn(GetBucketCorsResponse.builder()
            .corsRules(CORSRule.builder().id("other-app").allowedOrigins("https://other.example").allowedMethods("GET").build()).build());
        var storage=new B2ModelStorage(client,mock(S3Presigner.class),bucket,Duration.ofHours(1));
        storage.configureViewerCors(List.of("http://localhost:5173"));
        var request=org.mockito.ArgumentCaptor.forClass(PutBucketCorsRequest.class);
        verify(client).putBucketCors(request.capture());
        var rules=request.getValue().corsConfiguration().corsRules();
        assertEquals(2,rules.size());assertEquals("other-app",rules.get(0).id());
        assertEquals(List.of("GET","HEAD"),rules.get(1).allowedMethods());
    }
}

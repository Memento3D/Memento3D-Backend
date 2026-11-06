package com.memento3d.customization;

import com.memento3d.assets.infrastructure.storage.B2ModelStorage;
import com.memento3d.customization.application.service.CatalogManifestLoader;
import com.memento3d.customization.infrastructure.storage.B2CatalogObjectVerifier;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CatalogObjectVerifierTest {
    final S3Client client=mock(S3Client.class);
    final B2ModelStorage storage=new B2ModelStorage(client,mock(S3Presigner.class),"test-bucket",Duration.ofHours(1));
    final byte[] bytes="console-uploaded object without sha metadata".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    void object(byte[] data) {
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),new ByteArrayInputStream(data)));
    }
    @Test void fullStreamHashVerifiesConsoleUploadsWithoutTrustingMetadataOrEtag() throws Exception {
        object(bytes);storage.verifyObject("test-bucket","library/mvp-v1/x.glb",bytes.length,CatalogManifestLoader.digest(bytes));
        verify(client).getObject(any(GetObjectRequest.class));verifyNoMoreInteractions(client);
    }
    @ParameterizedTest @ValueSource(ints={-1,1})
    void truncatedAndOversizedObjectsAreRejected(int delta) {
        object(bytes);assertThrows(IllegalStateException.class,()->storage.verifyObject("test-bucket","library/mvp-v1/x.glb",bytes.length+delta,CatalogManifestLoader.digest(bytes)));
    }
    @Test void sameSizeWrongContentIsRejected() {
        object(bytes);assertThrows(IllegalStateException.class,()->storage.verifyObject("test-bucket","library/mvp-v1/x.glb",bytes.length,"f".repeat(64)));
    }
    @Test void unsafeKeysBucketAndHashesAreRejectedBeforeNetwork() {
        assertThrows(IllegalArgumentException.class,()->storage.verifyObject("other-bucket","library/a",1,"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->storage.verifyObject("test-bucket","library/../a",1,"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->storage.verifyObject("test-bucket","users/private",1,"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->storage.verifyObject("test-bucket","library/a",1,"bad"));verifyNoInteractions(client);
    }
    @SuppressWarnings("unchecked")
    @Test void adapterRequiresB2AndMapsRemoteErrorsWithoutLeakingDetails() throws Exception {
        ObjectProvider<B2ModelStorage> provider=mock(ObjectProvider.class);var verifier=new B2CatalogObjectVerifier(provider);
        assertEquals(503,assertThrows(BusinessException.class,()->verifier.verify("x","x",1,"x")).getStatus().value());
        var b2=mock(B2ModelStorage.class);when(provider.getIfAvailable()).thenReturn(b2);
        doThrow(new IllegalStateException("private backend detail")).when(b2).verifyObject(any(),any(),anyLong(),any());
        var conflict=assertThrows(BusinessException.class,()->verifier.verify("x","x",1,"x"));
        assertEquals(409,conflict.getStatus().value());assertFalse(conflict.getMessage().contains("private backend detail"));
        doThrow(new java.io.IOException("private transport detail")).when(b2).verifyObject(any(),any(),anyLong(),any());
        assertEquals(503,assertThrows(BusinessException.class,()->verifier.verify("x","x",1,"x")).getStatus().value());
    }
}

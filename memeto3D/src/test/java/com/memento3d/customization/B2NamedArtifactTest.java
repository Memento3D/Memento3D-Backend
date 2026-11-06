package com.memento3d.customization;

import com.memento3d.assets.infrastructure.storage.B2ModelStorage;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class B2NamedArtifactTest {
    @Test void baseAndAccessoriesUseDifferentImmutableUserJobAttemptKeys() {
        var client=mock(S3Client.class);Map<String,HeadObjectResponse> objects=new HashMap<>();
        when(client.headObject(any(HeadObjectRequest.class))).thenAnswer(call->{
            String k=((HeadObjectRequest)call.getArgument(0)).key();
            if(!objects.containsKey(k))throw S3Exception.builder().statusCode(404).build();return objects.get(k);
        });
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenAnswer(call->{
            PutObjectRequest p=call.getArgument(0);objects.put(p.key(),HeadObjectResponse.builder().contentLength(p.contentLength()).metadata(p.metadata()).eTag("fixture-etag").build());
            return PutObjectResponse.builder().eTag("fixture-etag").build();
        });
        var storage=new B2ModelStorage(client,mock(S3Presigner.class),"memento3d-models-tiendon",Duration.ofHours(1));
        UUID u=UUID.randomUUID(),s=UUID.randomUUID(),j=UUID.randomUUID(),a=UUID.randomUUID();byte[] bytes=FitFixtures.glb("base");
        var base=storage.uploadGlb(bytes,u,s,j,a);
        var hair=storage.uploadGlbArtifact(bytes,u,s,j,a,"customization/hair-side-part.glb");
        var glasses=storage.uploadGlbArtifact(bytes,u,s,j,a,"customization/glasses-round.glb");
        assertEquals(3,Set.of(base.objectKey(),hair.objectKey(),glasses.objectKey()).size());
        assertTrue(hair.objectKey().contains("/users/"+u+"/scans/"+s+"/jobs/"+j+"/attempts/"+a+"/customization/"));
        assertEquals(hair,storage.uploadGlbArtifact(bytes,u,s,j,a,"customization/hair-side-part.glb"));
        assertThrows(IllegalStateException.class,()->storage.uploadGlbArtifact(FitFixtures.glb("different"),u,s,j,a,"customization/hair-side-part.glb"));
        verify(client,times(3)).putObject(any(PutObjectRequest.class),any(RequestBody.class));
        assertThrows(IllegalArgumentException.class,()->storage.uploadGlbArtifact(bytes,u,s,j,a,"../shared.glb"));
    }
}

package com.memento3d.customization;

import com.memento3d.assets.domain.port.ModelStoragePort;
import com.memento3d.customization.application.service.*;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomizationFitIngestionTest {
    final AiGenerationClient ai=mock(AiGenerationClient.class);final ModelStoragePort storage=mock(ModelStoragePort.class);
    final CustomizationCatalogRepository catalogs=mock(CustomizationCatalogRepository.class);
    final CustomizationCatalogMemberRepository members=mock(CustomizationCatalogMemberRepository.class);
    final CustomizationFitResultRepository fits=mock(CustomizationFitResultRepository.class);
    final CustomizationFitAssetRepository assets=mock(CustomizationFitAssetRepository.class);
    final CustomizationCatalog catalog=FitFixtures.catalog();final List<CustomizationAssetVersion> versions=FitFixtures.versions();
    final UUID job=UUID.randomUUID(),attempt=UUID.randomUUID(),user=UUID.randomUUID(),scan=UUID.randomUUID();final String hash="a".repeat(64);
    final CustomizationFitIngestion service=new CustomizationFitIngestion(ai,storage,new CatalogManifestLoader(FitFixtures.MAPPER),new CustomizationFitValidator(FitFixtures.MAPPER),catalogs,members,fits,assets,FitFixtures.MAPPER);
    @BeforeEach void configure() throws Exception {
        when(storage.provider()).thenReturn("b2");when(catalogs.findByCode("mvp-v1")).thenReturn(Optional.of(catalog));
        when(catalogs.findByCodeForUpdate("mvp-v1")).thenReturn(Optional.of(catalog));
        when(members.findMembers(catalog.getId())).thenReturn(versions.stream().map(v->{var m=new CustomizationCatalogMember();m.setAssetVersion(v);return m;}).toList());
        when(ai.getCustomizationManifest(job,attempt)).thenReturn(FitFixtures.download(job,attempt,hash));
        when(ai.downloadCustomizationAsset(eq(job),eq(attempt),anyString())).thenAnswer(c->FitFixtures.glb(c.getArgument(2)));
        when(storage.uploadGlbArtifact(any(),eq(user),eq(scan),eq(job),eq(attempt),anyString())).thenAnswer(c->{
            byte[] b=c.getArgument(0);String key="memento3d/users/"+user+"/scans/"+scan+"/jobs/"+job+"/attempts/"+attempt+"/"+c.getArgument(5);
            return new ModelStoragePort.StoredModel("b2",key,"fixture-etag","b2://memento3d-models-tiendon/"+key,b.length);
        });
    }
    @Test void capIsReferencedOnceWhileOnlyTwoFittedMeshesAreUploaded() throws Exception {
        var prepared=service.prepare(job,attempt,user,scan,hash);assertEquals(3,prepared.assets().size());
        verify(ai,never()).downloadCustomizationAsset(job,attempt,"graduation-cap");
        verify(storage,times(2)).uploadGlbArtifact(any(),eq(user),eq(scan),eq(job),eq(attempt),startsWith("customization/"));
        assertTrue(prepared.assets().stream().filter(a->a.geometryMode().equals("shared_transform")).findFirst().orElseThrow().file().objectKey().startsWith("library/"));
    }
    @Test void wrongBaseOrAssetHashCannotPublishAnyDatabaseRecords() throws Exception {
        assertThrows(BusinessException.class,()->service.prepare(job,attempt,user,scan,"b".repeat(64)));
        verify(storage,never()).uploadGlbArtifact(any(),any(),any(),any(),any(),any());verifyNoInteractions(fits,assets);
        when(ai.downloadCustomizationAsset(job,attempt,"hair-side-part")).thenReturn(FitFixtures.glb("wrong"));
        assertThrows(BusinessException.class,()->service.prepare(job,attempt,user,scan,hash));verifyNoInteractions(fits,assets);
    }
    @Test void cloudinaryOrInactiveCatalogCannotBecomeACustomScene() {
        when(storage.provider()).thenReturn("cloudinary");assertThrows(BusinessException.class,()->service.prepare(job,attempt,user,scan,hash));
        when(storage.provider()).thenReturn("b2");catalog.setStatus("DRAFT");assertThrows(BusinessException.class,()->service.prepare(job,attempt,user,scan,hash));
        verifyNoInteractions(fits,assets);
    }
    @Test void badStorageOwnershipOrSizeIsRejected() throws Exception {
        when(storage.uploadGlbArtifact(any(),any(),any(),any(),any(),any())).thenReturn(new ModelStoragePort.StoredModel("b2","another-user/file.glb","x","b2://wrong/file.glb",1));
        assertThrows(BusinessException.class,()->service.prepare(job,attempt,user,scan,hash));verifyNoInteractions(fits,assets);
    }
    @Test void persistenceCreatesOneResultWithThreeVersionBindingsAndStableRefs() {
        var prepared=service.prepare(job,attempt,user,scan,hash);UUID result=service.persist(job,attempt,user,scan,prepared);assertNotNull(result);
        verify(fits).save(argThat(f->job.equals(f.getJobId()) && attempt.equals(f.getAttemptId()) && user.equals(f.getUserId()) && scan.equals(f.getScanId())));
        verify(assets,times(3)).save(argThat(a->a.getLocation().startsWith("b2://") && !a.getLocation().contains("Signature")));
    }
    @Test void repeatedResultIsIdempotentButDifferentOwnerCannotReuseIt() {
        var prepared=service.prepare(job,attempt,user,scan,hash);var existing=new CustomizationFitResult();existing.setId(UUID.randomUUID());
        existing.setManifestSha256(prepared.manifestSha256());existing.setBaseSha256(hash);existing.setUserId(user);existing.setScanId(scan);
        when(fits.findByJobIdAndAttemptId(job,attempt)).thenReturn(Optional.of(existing));
        assertEquals(existing.getId(),service.persist(job,attempt,user,scan,prepared));verifyNoInteractions(assets);
        assertThrows(BusinessException.class,()->service.persist(job,attempt,UUID.randomUUID(),scan,prepared));
    }
}

package com.memento3d.customization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.application.service.*;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.domain.port.CatalogObjectVerifier;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.models.application.service.ModelDeliveryService;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssetCatalogServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    final CatalogManifestLoader loader=new CatalogManifestLoader(mapper);
    final CatalogObjectVerifier verifier=mock(CatalogObjectVerifier.class);
    final CatalogImportWriter writer=mock(CatalogImportWriter.class);
    final CustomizationCatalogRepository catalogs=mock(CustomizationCatalogRepository.class);
    final CustomizationCatalogMemberRepository members=mock(CustomizationCatalogMemberRepository.class);
    final ModelDeliveryService delivery=mock(ModelDeliveryService.class);
    final AssetCatalogService service=new AssetCatalogService(loader,verifier,writer,catalogs,members,delivery,mapper);

    @Test void verifiesEveryObjectBeforeCallingTransactionalWriter() {
        var source=loader.load("mvp-v1");service.importCatalog("mvp-v1");
        var order=inOrder(verifier,writer);
        for(var pin:source.manifest().files())order.verify(verifier).verify(source.manifest().bucket(),pin.key(),pin.bytes(),pin.sha256());
        order.verify(writer).persistVerified(source);verifyNoMoreInteractions(writer);
    }
    @Test void failedStorageVerificationNeverOpensAnImportTransaction() {
        doThrow(new IllegalStateException("fixture mismatch")).when(verifier).verify(any(),any(),anyLong(),any());
        assertThrows(IllegalStateException.class,()->service.importCatalog("mvp-v1"));verifyNoInteractions(writer,catalogs,members);
    }
    @Test void unknownAndInactiveCatalogsCannotBeDelivered() {
        when(catalogs.findByCode("missing")).thenReturn(Optional.empty());
        assertEquals(404,assertThrows(BusinessException.class,()->service.getCatalog("missing")).getStatus().value());
        var catalog=new CustomizationCatalog();catalog.setStatus("DRAFT");
        when(catalogs.findByCode("draft")).thenReturn(Optional.of(catalog));
        assertEquals(409,assertThrows(BusinessException.class,()->service.getCatalog("draft")).getStatus().value());
        verifyNoInteractions(delivery,members);
    }
    @Test void activeCatalogUsesFreshSignedUrlAndStructuredGroupsWithoutExposingB2Refs() {
        var catalog=new CustomizationCatalog();catalog.setId(UUID.randomUUID());catalog.setCode("mvp-v1");catalog.setStatus("ACTIVE");
        catalog.setGroupsJson("[{\"id\":\"headwear\"}]");catalog.setDefaultSelectionJson("{\"headwear\":\"cap\"}");
        var asset=new CustomizationAssetVersion();asset.setId(UUID.randomUUID());asset.setAssetCode("cap");asset.setVersion("v1");
        asset.setBucket("bucket");asset.setObjectKey("library/cap.glb");asset.setCompatibilityJson("{\"requiresPerJobFit\":true}");
        var member=new CustomizationCatalogMember();member.setAssetVersion(asset);
        when(catalogs.findByCode("mvp-v1")).thenReturn(Optional.of(catalog));when(members.findMembers(catalog.getId())).thenReturn(List.of(member));
        when(delivery.url("b2","library/cap.glb","b2://bucket/library/cap.glb")).thenReturn("https://example.test/fresh-signed");
        when(delivery.urlTtlSeconds()).thenReturn(3600L);
        var response=service.getCatalog("mvp-v1");assertEquals(3600,response.urlTtlSeconds());assertEquals("headwear",response.groups().get(0).get("id"));
        assertEquals("https://example.test/fresh-signed",response.assets().get(0).previewUrl());assertFalse(response.assets().get(0).printReady());
    }
    @Test void corruptPersistedJsonFailsInsteadOfReturningAnInventedScene() {
        var catalog=new CustomizationCatalog();catalog.setId(UUID.randomUUID());catalog.setStatus("ACTIVE");catalog.setGroupsJson("bad json");
        when(catalogs.findByCode("broken")).thenReturn(Optional.of(catalog));when(members.findMembers(catalog.getId())).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->service.getCatalog("broken"));
    }
}

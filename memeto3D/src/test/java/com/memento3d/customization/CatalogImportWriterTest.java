package com.memento3d.customization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.application.service.*;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CatalogImportWriterTest {
    final CustomizationCatalogRepository catalogs=mock(CustomizationCatalogRepository.class);
    final CustomizationAssetVersionRepository versions=mock(CustomizationAssetVersionRepository.class);
    final CustomizationCatalogMemberRepository members=mock(CustomizationCatalogMemberRepository.class);
    final ObjectMapper mapper=new ObjectMapper();
    final CatalogImportWriter writer=new CatalogImportWriter(catalogs,versions,members,mapper);
    final CatalogManifestLoader.Loaded loaded=new CatalogManifestLoader(mapper).load("mvp-v1");
    CustomizationCatalog catalog(String status) {
        var c=new CustomizationCatalog();c.setId(UUID.randomUUID());c.setCode("mvp-v1");c.setBodyPreset(loaded.manifest().bodyPreset());c.setStatus(status);
        when(catalogs.findByCodeForUpdate("mvp-v1")).thenReturn(Optional.of(c));return c;
    }
    @Test void newImportCreatesThreeImmutableVersionsAndActivatesOnlyAtTheEnd() {
        var c=catalog("DRAFT");var result=writer.persistVerified(loaded);
        verify(versions,times(3)).save(any());verify(members,times(3)).save(any());
        assertEquals("ACTIVE",c.getStatus());assertNotNull(c.getVerifiedAt());assertEquals(15,result.verifiedObjects());assertFalse(result.unchanged());
        var order=inOrder(versions,members,catalogs);order.verify(versions).save(any());order.verify(members).save(any());
    }
    @Test void repeatedIdenticalImportDoesNotCreateMoreVersionsOrMembers() {
        var c=catalog("ACTIVE");c.setImportSha256(loaded.sha256());c.setVerifiedObjectCount(15);
        when(members.findMembers(c.getId())).thenReturn(List.of(new CustomizationCatalogMember()));
        assertTrue(writer.persistVerified(loaded).unchanged());verifyNoInteractions(versions);verify(members,never()).save(any());
    }
    @Test void alteredCatalogOrArchivedVersionCannotBeOverwritten() {
        var c=catalog("ACTIVE");c.setImportSha256("different");assertThrows(BusinessException.class,()->writer.persistVerified(loaded));
        c.setStatus("ARCHIVED");assertThrows(BusinessException.class,()->writer.persistVerified(loaded));verifyNoInteractions(versions);
    }
    @Test void anExistingAssetVersionWithDifferentMetadataCannotBeReused() {
        catalog("DRAFT");var existing=new CustomizationAssetVersion();existing.setMetadataSha256("different");
        var first=loaded.manifest().assets().get(0);when(versions.findByAssetCodeAndVersion(first.code(),first.version())).thenReturn(Optional.of(existing));
        assertThrows(BusinessException.class,()->writer.persistVerified(loaded));verify(catalogs,never()).saveAndFlush(any());
    }
}

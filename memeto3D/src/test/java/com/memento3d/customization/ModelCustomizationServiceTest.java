package com.memento3d.customization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.api.request.SaveCustomizationRequest;
import com.memento3d.customization.application.service.ModelCustomizationService;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.models.application.service.ModelDeliveryService;
import com.memento3d.models.domain.model.ModelOutput;
import com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository;
import com.memento3d.reconstruction.application.dto.AiCustomizationManifest;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.scans.application.service.ScanAccessGuard;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelCustomizationServiceTest {
    final ModelOutputRepository outputs=mock(ModelOutputRepository.class);
    final CustomizationFitResultRepository fits=mock(CustomizationFitResultRepository.class);
    final CustomizationFitAssetRepository placements=mock(CustomizationFitAssetRepository.class);
    final CustomizationCatalogRepository catalogs=mock(CustomizationCatalogRepository.class);
    final CustomizationCatalogMemberRepository members=mock(CustomizationCatalogMemberRepository.class);
    final CustomizationDraftRepository drafts=mock(CustomizationDraftRepository.class);
    final ObjectMapper mapper=new ObjectMapper();
    final ModelCustomizationService service=new ModelCustomizationService(outputs,fits,placements,catalogs,members,drafts,
        mock(ScanAccessGuard.class),mock(ModelDeliveryService.class),mapper);
    final UUID outputId=UUID.randomUUID(),jobId=UUID.randomUUID(),attempt=UUID.randomUUID(),owner=UUID.randomUUID();
    final UUID fitId=UUID.randomUUID(),scanId=UUID.randomUUID(),catalogId=UUID.randomUUID(),cap=UUID.randomUUID(),hair=UUID.randomUUID();
    final CustomizationCatalog catalog=new CustomizationCatalog();

    @BeforeEach void setup() throws Exception {
        ModelOutput output=mock(ModelOutput.class);ReconstructionJob job=mock(ReconstructionJob.class);
        Scan scan=mock(Scan.class,RETURNS_DEEP_STUBS);
        when(scan.getDeletedAt()).thenReturn(null);when(output.getId()).thenReturn(outputId);when(output.getJob()).thenReturn(job);when(job.getId()).thenReturn(jobId);
        when(job.getScan()).thenReturn(scan);when(scan.getId()).thenReturn(scanId);when(scan.getUser().getId()).thenReturn(owner);
        when(output.getAttemptId()).thenReturn(attempt);when(output.getFormat()).thenReturn("glb");when(output.getSha256()).thenReturn("base");
        when(outputs.findSceneOutput(outputId)).thenReturn(Optional.of(output));when(outputs.findSceneOutputForUpdate(outputId)).thenReturn(Optional.of(output));
        var fit=new CustomizationFitResult();fit.setId(fitId);fit.setUserId(owner);fit.setScanId(scanId);fit.setBaseSha256("base");fit.setCatalogId(catalogId);
        var snapshot=new AiCustomizationManifest(1,"policy",jobId,attempt,"mvp-v1","catalog","profile",
            new AiCustomizationManifest.Base("base","customization_base"),Map.of("headwear","cap"),
            List.of(Map.of("id","headwear","required",true,"assets",List.of("cap","hair"))),List.of(),"Y-up","NEEDS_VISUAL_REVIEW",false);
        fit.setSceneJson(mapper.writeValueAsString(snapshot));when(fits.findByJobIdAndAttemptId(jobId,attempt)).thenReturn(Optional.of(fit));
        catalog.setId(catalogId);catalog.setStatus("ACTIVE");when(catalogs.findById(catalogId)).thenReturn(Optional.of(catalog));
        var a=member(cap,"cap");var b=member(hair,"hair");when(members.findMembers(catalogId)).thenReturn(List.of(a,b));
        when(placements.findByFitResultId(fitId)).thenReturn(List.of(placement(cap),placement(hair)));
        when(drafts.findByModelOutputId(outputId)).thenReturn(Optional.empty());
    }
    CustomizationCatalogMember member(UUID id,String code) {
        var version=new CustomizationAssetVersion();version.setId(id);version.setAssetCode(code);version.setSlot("headwear");
        var member=new CustomizationCatalogMember();member.setAssetVersion(version);return member;
    }
    CustomizationFitAsset placement(UUID id) {var p=new CustomizationFitAsset();p.setAssetVersionId(id);return p;}
    CustomizationDraft saved(long revision,UUID selected) throws Exception {
        var draft=new CustomizationDraft();draft.setFitResultId(fitId);draft.setUserId(owner);draft.setRevision(revision);
        draft.setSelectionJson(mapper.writeValueAsString(Map.of("headwear",selected)));when(drafts.findByModelOutputId(outputId)).thenReturn(Optional.of(draft));return draft;
    }
    SaveCustomizationRequest request(long revision,UUID selected) {return new SaveCustomizationRequest(fitId,revision,Map.of("headwear",selected));}

    @Test void unsavedDefaultsAreReadWithoutWriting() {
        var response=service.getDraft(outputId,owner,Role.ROLE_USER);
        assertFalse(response.exists());assertEquals(0,response.revision());assertEquals(cap,response.selection().get("headwear"));
        verify(drafts,never()).saveAndFlush(any());
    }
    @Test void checkoutRequiresAnExplicitSaveAndExactRevision() throws Exception {
        assertThrows(BusinessException.class,()->service.freeze(outputId,fitId,0L,owner));
        saved(2,cap);
        assertThrows(BusinessException.class,()->service.freeze(outputId,fitId,1L,owner));
        assertThrows(BusinessException.class,()->service.freeze(outputId,UUID.randomUUID(),2L,owner));
    }
    @Test void frozenSelectionDoesNotFollowLaterDraftEdits() throws Exception {
        var capFit=placement(cap);var hairFit=placement(hair);
        String identity="[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]";
        capFit.setMatrixJson(identity);hairFit.setMatrixJson(identity);
        when(placements.findByFitResultId(fitId)).thenReturn(List.of(capFit,hairFit));
        saved(2,cap);var frozen=service.freeze(outputId,fitId,2L,owner);
        saved(3,hair);
        assertEquals(cap,frozen.selection().get("headwear"));assertEquals(cap,frozen.assets().get(0).assetVersionId());
        assertEquals(2,frozen.revision());assertThrows(BusinessException.class,()->service.freeze(outputId,fitId,2L,owner));
    }
    @Test void firstSaveCreatesRevisionOneWithoutGeneratingModel() {
        var response=service.save(outputId,request(0,hair),owner,Role.ROLE_USER);
        assertTrue(response.exists());assertEquals(1,response.revision());assertEquals(hair,response.selection().get("headwear"));
        verify(outputs).findSceneOutputForUpdate(outputId);verify(drafts).saveAndFlush(any());
    }
    @Test void retrySameSelectionDoesNotIncrementRevision() throws Exception {
        saved(3,hair);assertEquals(3,service.save(outputId,request(2,hair),owner,Role.ROLE_USER).revision());
        verify(drafts,never()).saveAndFlush(any());
    }
    @Test void staleRevisionCannotReplaceSavedSelection() throws Exception {
        saved(3,hair);assertThrows(BusinessException.class,()->service.save(outputId,request(2,cap),owner,Role.ROLE_USER));
        verify(drafts,never()).saveAndFlush(any());
    }
    @Test void foreignAssetAndNonOwnerCannotSave() {
        assertThrows(BusinessException.class,()->service.save(outputId,request(0,UUID.randomUUID()),owner,Role.ROLE_USER));
        assertThrows(BusinessException.class,()->service.save(outputId,request(0,cap),UUID.randomUUID(),Role.ROLE_ADMIN));
        verify(drafts,never()).saveAndFlush(any());
    }
    @Test void archivedCatalogRemainsReadableButCannotChange() {
        catalog.setStatus("ARCHIVED");assertFalse(service.getDraft(outputId,owner,Role.ROLE_USER).editable());
        assertThrows(BusinessException.class,()->service.save(outputId,request(0,hair),owner,Role.ROLE_USER));
        verify(drafts,never()).saveAndFlush(any());
    }
}

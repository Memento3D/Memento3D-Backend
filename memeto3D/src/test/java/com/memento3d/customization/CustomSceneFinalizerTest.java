package com.memento3d.customization;

import com.memento3d.assets.domain.port.ModelStoragePort;
import com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository;
import com.memento3d.customization.application.service.CustomizationFitIngestion;
import com.memento3d.identity.domain.model.User;
import com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository;
import com.memento3d.reconstruction.application.dto.AiJobResponse;
import com.memento3d.reconstruction.application.service.impl.ReconstructionJobFinalizerImpl;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.domain.model.*;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.scans.application.service.ScanStatusCacheService;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomSceneFinalizerTest {
    final ReconstructionJobRepository jobs=mock(ReconstructionJobRepository.class);
    final ModelOutputRepository outputs=mock(ModelOutputRepository.class);final AiGenerationClient ai=mock(AiGenerationClient.class);
    final ModelStoragePort storage=mock(ModelStoragePort.class);final ScanStatusCacheService cache=mock(ScanStatusCacheService.class);
    final PlatformTransactionManager tx=mock(PlatformTransactionManager.class);final DigitalAssetRepository digital=mock(DigitalAssetRepository.class);
    final CustomizationFitIngestion fits=mock(CustomizationFitIngestion.class);final OutboxPublisherService outbox=mock(OutboxPublisherService.class);
    final UUID id=UUID.randomUUID(),attempt=UUID.randomUUID(),u=UUID.randomUUID(),s=UUID.randomUUID();
    ReconstructionJob job;ReconstructionJobFinalizerImpl service;AiJobResponse ready;
    final CustomizationFitIngestion.Prepared prepared=new CustomizationFitIngestion.Prepared(UUID.randomUUID(),"mvp-v1","x","x","x","x","mvp_landmark_fit_v1","{}",List.of(),id,attempt,u,s);
    @BeforeEach void configure() throws Exception {
        job=ReconstructionJob.builder().id(id).scan(Scan.builder().id(s).user(User.builder().id(u).build()).build()).attemptId(attempt).status(ReconstructionJobStatus.running).build();
        when(tx.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(jobs.findByIdForUpdate(id)).thenReturn(Optional.of(job));
        when(ai.downloadGlb(id,attempt)).thenReturn(FitFixtures.glb("base"));
        when(fits.prepare(eq(id),eq(attempt),eq(u),eq(s),anyString())).thenReturn(prepared);
        when(storage.uploadGlb(any(),eq(u),eq(s),eq(id),eq(attempt))).thenReturn(new ModelStoragePort.StoredModel("b2","base","etag","b2://bucket/base",32));
        when(storage.downloadUrl("base","b2://bucket/base")).thenReturn("https://example.test/signed");
        service=new ReconstructionJobFinalizerImpl(jobs,outputs,ai,storage,cache,tx,digital,outbox,fits);
        ready=new AiJobResponse(id,attempt,"done",null,100,null,null,null,null,null,"customization_base",true,false);
    }
    @Test void customFitAndBaseAreCommittedBeforeDoneAndNoPrintApprovalIsCreated() {
        job.setTier(ReconstructionTier.HIGH_QUALITY);service.finish(id,attempt,ready);
        assertEquals(ReconstructionJobStatus.done,job.getStatus());assertTrue(job.isCustomizationReady());assertEquals("customization_base",job.getOutputKind());
        var order=inOrder(outputs,fits,jobs);order.verify(outputs).save(any());order.verify(fits).persist(id,attempt,u,s,prepared);order.verify(jobs).saveAndFlush(job);
        verifyNoInteractions(digital);verify(ai,never()).getJob(any());
    }
    @Test void unreadyOrWrongAttemptCannotDownloadOrPublishBase() {
        var unready=new AiJobResponse(id,attempt,"done",null,100,null,null,null,null,null,"customization_base",false,false);
        assertThrows(IllegalStateException.class,()->service.finish(id,attempt,unready));verify(ai,never()).downloadGlb(any(),any());verifyNoInteractions(outputs);
        service.finish(id,UUID.randomUUID(),ready);verifyNoInteractions(fits,outputs,storage);
    }
    @Test void failedFitCannotMarkJobDone() {
        when(fits.prepare(eq(id),eq(attempt),eq(u),eq(s),any())).thenThrow(new IllegalStateException("fixture bad manifest"));
        assertThrows(IllegalStateException.class,()->service.finish(id,attempt,ready));assertEquals(ReconstructionJobStatus.running,job.getStatus());
        verifyNoInteractions(outputs,storage);verify(fits,never()).persist(any(),any(),any(),any(),any());
    }
    @Test void cancelledWhileDownloadingDoesNotPublishFitOrBase() {
        when(ai.downloadGlb(id,attempt)).thenAnswer(c->{job.setStatus(ReconstructionJobStatus.cancelled);return FitFixtures.glb("base");});
        service.finish(id,attempt,ready);verify(outputs,never()).save(any());verify(fits,never()).persist(any(),any(),any(),any(),any());
        assertFalse(job.isCustomizationReady());
    }
    @Test void failurePersistingFitDoesNotPublishDoneOrEvents() {
        when(fits.persist(id,attempt,u,s,prepared)).thenThrow(new IllegalStateException("fixture database failure"));
        assertThrows(IllegalStateException.class,()->service.finish(id,attempt,ready));assertEquals(ReconstructionJobStatus.running,job.getStatus());
        verifyNoInteractions(outbox,cache);
    }
}

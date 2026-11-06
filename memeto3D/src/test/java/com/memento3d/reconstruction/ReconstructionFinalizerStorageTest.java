package com.memento3d.reconstruction;

import com.memento3d.assets.domain.port.ModelStoragePort;
import com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository;
import com.memento3d.identity.domain.model.User;
import com.memento3d.models.domain.model.ModelOutput;
import com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository;
import com.memento3d.reconstruction.application.service.impl.ReconstructionJobFinalizerImpl;
import com.memento3d.reconstruction.domain.gateway.AiGenerationClient;
import com.memento3d.reconstruction.domain.model.*;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.scans.application.service.ScanStatusCacheService;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReconstructionFinalizerStorageTest {
    private final ReconstructionJobRepository jobs=mock(ReconstructionJobRepository.class);
    private final ModelOutputRepository outputs=mock(ModelOutputRepository.class);
    private final AiGenerationClient ai=mock(AiGenerationClient.class);
    private final ModelStoragePort storage=mock(ModelStoragePort.class);
    private final ScanStatusCacheService cache=mock(ScanStatusCacheService.class);
    private final PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    private final UUID id=UUID.randomUUID(), attempt=UUID.randomUUID();
    private ReconstructionJob job;
    private ReconstructionJobFinalizerImpl service;
    @BeforeEach void setup() {
        Scan scan=Scan.builder().id(UUID.randomUUID()).user(User.builder().id(UUID.randomUUID()).build()).build();
        job=ReconstructionJob.builder().id(id).attemptId(attempt).status(ReconstructionJobStatus.running).tier(ReconstructionTier.PREVIEW).scan(scan).build();
        when(tx.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(jobs.findByIdForUpdate(id)).thenReturn(Optional.of(job));
        when(ai.getJob(id)).thenReturn(new com.memento3d.reconstruction.application.dto.AiJobResponse(id,attempt,"done",null,100,null,null,null,null,null));
        service=new ReconstructionJobFinalizerImpl(jobs,outputs,ai,storage,cache,tx,
                mock(DigitalAssetRepository.class),mock(OutboxPublisherService.class));
    }
    @Test void successfulB2UploadStoresStableReferenceBeforeMarkingDone() throws Exception {
        byte[] bytes=new byte[12];when(ai.downloadGlb(id,attempt)).thenReturn(bytes);
        when(storage.uploadGlb(eq(bytes),any(),any(),eq(id),eq(attempt))).thenReturn(
            new ModelStoragePort.StoredModel("b2","job/model.glb","etag","b2://bucket/job/model.glb",12));
        when(storage.downloadUrl("job/model.glb","b2://bucket/job/model.glb")).thenReturn("https://example.test/signed");
        service.finish(id,attempt);
        var saved=org.mockito.ArgumentCaptor.forClass(ModelOutput.class);verify(outputs).save(saved.capture());
        assertEquals("b2",saved.getValue().getStorageProvider());
        assertEquals("b2://bucket/job/model.glb",saved.getValue().getDeliveryUrl());
        assertEquals("job/model.glb",saved.getValue().getStoragePublicId());
        assertEquals(ReconstructionJobStatus.done,job.getStatus());assertEquals(100,job.getProgress());
        assertNotNull(job.getCompletedAt());verify(outputs).flush();
    }
    @Test void failedUploadCannotPublishSuccessfulOutput() throws Exception {
        when(ai.downloadGlb(id,attempt)).thenReturn(new byte[12]);
        when(storage.uploadGlb(any(),any(),any(),any(),any())).thenThrow(new IOException("fixture storage unavailable"));
        assertThrows(IllegalStateException.class,()->service.finish(id,attempt));
        assertEquals(ReconstructionJobStatus.running,job.getStatus());
        verify(outputs,never()).save(any());verifyNoInteractions(cache);
    }
    @Test void staleAttemptDoesNotDownloadOrUpload() {
        service.finish(id,UUID.randomUUID());verifyNoInteractions(ai,storage,outputs);
    }
}

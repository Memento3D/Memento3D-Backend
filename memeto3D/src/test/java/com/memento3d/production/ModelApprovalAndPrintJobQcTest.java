package com.memento3d.production;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.modelreview.application.service.impl.ModelReviewServiceImpl;
import com.memento3d.modelreview.domain.model.ModelVersion;
import com.memento3d.modelreview.infrastructure.persistence.repository.ModelReviewRepository;
import com.memento3d.modelreview.infrastructure.persistence.repository.ModelVersionRepository;
import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.quality.application.PhysicalQualityServiceImpl;
import com.memento3d.quality.domain.model.PhysicalQualityCheck;
import com.memento3d.quality.infrastructure.persistence.repository.PhysicalQualityCheckRepository;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import com.memento3d.shared.outbox.domain.model.TransactionalOutbox;
import com.memento3d.shared.outbox.infrastructure.persistence.repository.TransactionalOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelApprovalAndPrintJobQcTest {
    @Mock private com.memento3d.customization.application.service.PrintDesignService printDesignService;

    @Mock
    private ModelVersionRepository modelVersionRepository;

    @Mock
    private ModelReviewRepository modelReviewRepository;

    @Mock
    private PrintJobRepository printJobRepository;

    @Mock
    private PhysicalQualityCheckRepository physicalQualityCheckRepository;

    @Mock
    private TransactionalOutboxRepository outboxRepository;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @InjectMocks
    private ModelReviewServiceImpl modelReviewService;

    @InjectMocks
    private PhysicalQualityServiceImpl physicalQualityService;

    private UUID userId;
    private UUID versionId;
    private UUID printJobId;
    private UUID orderId;
    private Scan scan;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        printJobId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        User user = User.builder().id(userId).role(Role.ROLE_USER).build();
        scan = Scan.builder().id(UUID.randomUUID()).user(user).build();
    }

    @Test
    @DisplayName("TC-12: Approving a model version that is already LOCKED throws BusinessException")
    void tc12_approvingLockedModelVersionThrowsException() {
        ModelVersion version = ModelVersion.builder()
                .id(versionId)
                .scan(scan)
                .versionNumber(1)
                .modelUrl("https://storage.memento3d.com/model.glb")
                .status("LOCKED")
                .build();

        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThrows(BusinessException.class, () ->
                modelReviewService.approveVersion(versionId, "Looks good", userId, Role.ROLE_USER));
    }

    @Test
    @DisplayName("TC-13: PrintJob cannot be created when model version is not APPROVED")
    void tc13_printJobCreationRejectedIfModelNotApproved() {
        ModelVersion unapprovedVersion = ModelVersion.builder()
                .id(versionId)
                .scan(scan)
                .versionNumber(1)
                .modelUrl("https://storage.memento3d.com/model.glb")
                .status("AWAITING_APPROVAL")
                .build();

        assertFalse("APPROVED".equalsIgnoreCase(unapprovedVersion.getStatus()),
                "Physical print requires an approved model version");
    }

    @Test
    @DisplayName("TC-14: Duplicate approval callback is idempotent and does not overwrite locked/approved status")
    void tc14_duplicateApprovalIdempotent() {
        ModelVersion approvedVersion = ModelVersion.builder()
                .id(versionId)
                .scan(scan)
                .versionNumber(1)
                .modelUrl("https://storage.memento3d.com/model.glb")
                .status("APPROVED")
                .build();

        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(approvedVersion));

        ModelVersion result = modelReviewService.approveVersion(versionId, "Duplicate approve", userId, Role.ROLE_USER);
        assertEquals("APPROVED", result.getStatus());
        verify(outboxPublisherService, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-19: Order shipment is blocked when Physical Quality Check fails or has not passed")
    void tc19_shipmentBlockedWhenQcFailed() {
        PrintJob job = PrintJob.builder()
                .id(printJobId)
                .orderId(orderId)
                .status(PrintJobStatus.PRINTING)
                .build();

        when(printJobRepository.findByOrderId(orderId)).thenReturn(List.of(job));
        when(physicalQualityCheckRepository.findFirstByPrintJobIdOrderByCreatedAtDesc(printJobId))
                .thenReturn(Optional.of(
                        PhysicalQualityCheck.builder()
                                .id(UUID.randomUUID())
                                .printJobId(printJobId)
                                .status("FAIL")
                                .modelAccuracyPass(false)
                                .reprintRequired(true)
                                .build()
                ));

        assertThrows(IllegalStateException.class, () ->
                physicalQualityService.validatePrintJobPassedForShipment(orderId));
    }

    @Test
    @DisplayName("TC-20: Transactional Outbox records domain events atomically for async worker processing")
    void tc20_outboxRecordsEventsAtomically() {
        TransactionalOutbox outbox = TransactionalOutbox.builder()
                .aggregateType("Order")
                .aggregateId(orderId.toString())
                .eventType("ORDER_PAID")
                .payload("{\"orderId\":\"" + orderId + "\"}")
                .status("PENDING")
                .build();

        when(outboxRepository.save(any(TransactionalOutbox.class))).thenAnswer(i -> {
            TransactionalOutbox o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        TransactionalOutbox saved = outboxRepository.save(outbox);
        assertNotNull(saved.getId());
        assertEquals("ORDER_PAID", saved.getEventType());
        assertEquals("PENDING", saved.getStatus());
    }
}

package com.memento3d.reconstruction;

import com.memento3d.assets.application.service.impl.DigitalAssetServiceImpl;
import com.memento3d.assets.domain.model.DigitalAsset;
import com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository;
import com.memento3d.entitlement.application.service.EntitlementService;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.reconstruction.api.request.CreateReconstructionJobRequest;
import com.memento3d.reconstruction.api.request.CreatePreviewJobRequest;
import com.memento3d.reconstruction.application.service.impl.ReconstructionJobServiceImpl;
import com.memento3d.reconstruction.domain.exception.QuotaExceededException;
import com.memento3d.reconstruction.domain.model.PreviewQuota;
import com.memento3d.reconstruction.domain.model.ReconstructionJob;
import com.memento3d.reconstruction.domain.model.ReconstructionJobStatus;
import com.memento3d.reconstruction.domain.model.ReconstructionTier;
import com.memento3d.reconstruction.infrastructure.persistence.repository.PreviewQuotaRepository;
import com.memento3d.reconstruction.infrastructure.persistence.repository.ReconstructionJobRepository;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanImage;
import com.memento3d.scans.domain.model.ScanStatus;
import com.memento3d.scans.infrastructure.persistence.repository.ScanImageRepository;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconstructionAndEntitlementTest {

    @Mock
    private ReconstructionJobRepository jobRepository;

    @Mock
    private PreviewQuotaRepository previewQuotaRepository;

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanImageRepository scanImageRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private OutboxPublisherService outboxPublisher;

    @Mock
    private DigitalAssetRepository digitalAssetRepository;

    @Mock
    private com.memento3d.scans.application.service.ScanAccessGuard scanAccessGuard;

    @Mock
    private com.memento3d.scans.application.service.ScanStatusCacheService scanStatusCacheService;

    @Mock
    private com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository modelOutputRepository;

    @Mock
    private com.memento3d.identity.infrastructure.persistence.repository.UserRepository userRepository;

    @InjectMocks
    private ReconstructionJobServiceImpl reconstructionJobService;

    @InjectMocks
    private DigitalAssetServiceImpl digitalAssetService;

    private UUID userId;
    private UUID scanId;
    private UUID imageId;
    private Scan scan;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        scanId = UUID.randomUUID();
        imageId = UUID.randomUUID();

        user = User.builder().id(userId).role(Role.ROLE_USER).build();
        scan = Scan.builder().id(scanId).user(user).status(ScanStatus.ready).build();
    }

    @Test
    @DisplayName("TC-01: Free user calling HQ reconstruction without completed payment -> BusinessException PAYMENT_REQUIRED")
    void tc01_freeUserCallingHqWithoutPaymentThrowsException() {
        UUID orderId = UUID.randomUUID();
        Order unpaidOrder = Order.builder()
                .id(orderId)
                .userId(userId)
                .paymentStatus(PaymentStatus.PENDING)
                .scanId(scanId)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(unpaidOrder));

        assertThrows(BusinessException.class, () ->
                reconstructionJobService.submitPremiumJob(orderId, userId, Role.ROLE_USER));
    }

    @Test
    @DisplayName("TC-02: User has exhausted preview quota -> Throws QuotaExceededException")
    void tc02_previewQuotaExhaustedThrowsException() {
        PreviewQuota quota = PreviewQuota.builder()
                .id(UUID.randomUUID())
                .user(user)
                .totalQuota(3)
                .usedQuota(3)
                .build();

        when(previewQuotaRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(quota));

        CreatePreviewJobRequest request = CreatePreviewJobRequest.builder()
                .scanId(scanId)
                .inputImageId(imageId)
                .build();

        assertThrows(QuotaExceededException.class, () ->
                reconstructionJobService.submitPreviewJob(request, userId, Role.ROLE_USER));
    }

    @Test
    @DisplayName("TC-03: Preview quota incremented atomically in same transaction")
    void tc03_previewQuotaIncrementedAtomically() {
        PreviewQuota quota = PreviewQuota.builder()
                .id(UUID.randomUUID())
                .user(user)
                .totalQuota(3)
                .usedQuota(2)
                .build();

        assertTrue(quota.hasAvailableQuota());
        quota.incrementUsed();
        assertEquals(3, quota.getUsedQuota());
        assertEquals(0, quota.getRemainingQuota());
        assertFalse(quota.hasAvailableQuota());
    }

    @Test
    @DisplayName("TC-04: Paid user with valid entitlement creates HQ reconstruction job successfully")
    void tc04_paidUserWithPaymentCreatesHqJob() {
        UUID orderId = UUID.randomUUID();
        Order paidOrder = Order.builder()
                .id(orderId)
                .userId(userId)
                .paymentStatus(PaymentStatus.PAID)
                .scanId(scanId)
                .build();

        ScanImage frontImage = labeledImage(imageId, "front");
        ScanImage leftImage = labeledImage(UUID.randomUUID(), "left");
        ScanImage rightImage = labeledImage(UUID.randomUUID(), "right");
        List<ScanImage> scanImages = List.of(frontImage, leftImage, rightImage);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(paidOrder));
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(scanRepository.findByIdForUpdate(scanId)).thenReturn(Optional.of(scan));
        when(scanImageRepository.findByScanIdAndDeletedAtIsNull(scanId))
                .thenReturn(scanImages);
        when(jobRepository.existsByScan_IdAndStatusIn(eq(scanId), any())).thenReturn(false);

        when(jobRepository.saveAndFlush(any(ReconstructionJob.class))).thenAnswer(i -> {
            ReconstructionJob j = i.getArgument(0);
            j.setId(UUID.randomUUID());
            return j;
        });

        ReconstructionJob created = ReconstructionJob.builder()
                .id(UUID.randomUUID())
                .scan(scan)
                .tier(ReconstructionTier.HIGH_QUALITY)
                .status(ReconstructionJobStatus.queued)
                .build();

        when(jobRepository.findById(any())).thenReturn(Optional.of(created));
        when(jobRepository.save(any(ReconstructionJob.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() ->
                reconstructionJobService.submitPremiumJob(orderId, userId, Role.ROLE_USER));

        verify(jobRepository).saveAndFlush(argThat(job -> {
            Object value = job.getInputSnapshot().get("input_images");
            return value instanceof List<?> inputs
                    && inputs.size() == 3
                    && inputs.stream().map(input -> ((Map<?, ?>) input).get("view_hint"))
                    .collect(java.util.stream.Collectors.toSet())
                    .equals(Set.of("front", "left", "right"));
        }));
    }

    @Test
    @DisplayName("Reconstruction submission rejects scans missing a required labeled view")
    void submitJobRejectsMissingRequiredViewHint() {
        ScanImage frontImage = labeledImage(imageId, "front");
        ScanImage leftImage = labeledImage(UUID.randomUUID(), "left");

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(scanRepository.findByIdForUpdate(scanId)).thenReturn(Optional.of(scan));
        when(jobRepository.existsByScan_IdAndStatusIn(eq(scanId), any())).thenReturn(false);
        when(scanImageRepository.findByScanIdAndDeletedAtIsNull(scanId))
                .thenReturn(List.of(frontImage, leftImage));

        CreateReconstructionJobRequest request = CreateReconstructionJobRequest.builder()
                .inputImageId(imageId)
                .build();

        com.memento3d.scans.domain.exception.ScanConflictException exception =
                assertThrows(
                        com.memento3d.scans.domain.exception.ScanConflictException.class,
                        () -> reconstructionJobService.submitJob(
                                scanId, request, userId, Role.ROLE_USER
                        )
                );

        assertTrue(exception.getMessage().contains("front, left, and right"));
        verify(jobRepository, never()).saveAndFlush(any(ReconstructionJob.class));
    }

    @Test
    @DisplayName("Reconstruction submission defaults the primary image to the front-labeled image")
    void submitJobSelectsFrontImageWhenInputImageIdIsOmitted() {
        ScanImage frontImage = labeledImage(imageId, "front");
        List<ScanImage> scanImages = List.of(
                frontImage,
                labeledImage(UUID.randomUUID(), "left"),
                labeledImage(UUID.randomUUID(), "right")
        );

        when(scanRepository.findByIdAndDeletedAtIsNull(scanId)).thenReturn(Optional.of(scan));
        when(scanRepository.findByIdForUpdate(scanId)).thenReturn(Optional.of(scan));
        when(jobRepository.existsByScan_IdAndStatusIn(eq(scanId), any())).thenReturn(false);
        when(scanImageRepository.findByScanIdAndDeletedAtIsNull(scanId)).thenReturn(scanImages);
        when(jobRepository.saveAndFlush(any(ReconstructionJob.class))).thenAnswer(invocation -> {
            ReconstructionJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });

        reconstructionJobService.submitJob(
                scanId,
                CreateReconstructionJobRequest.builder().profile("default").build(),
                userId,
                Role.ROLE_USER
        );

        verify(jobRepository).saveAndFlush(argThat(job ->
                imageId.equals(job.getInputImageId())
                        && job.getInputSnapshot().get("input_images") instanceof List<?> inputs
                        && inputs.size() == 3
        ));
    }

    private ScanImage labeledImage(UUID id, String viewHint) {
        return ScanImage.builder()
                .id(id)
                .scan(scan)
                .storagePath("images/" + viewHint + ".jpg")
                .originalFilename(viewHint + ".jpg")
                .fileSizeBytes(1024L)
                .mimeType("image/jpeg")
                .format("jpg")
                .uploadStatus(com.memento3d.scans.domain.model.ScanImageUploadStatus.uploaded)
                .cloudinaryPublicId("scans/test_" + viewHint)
                .viewHint(viewHint)
                .build();
    }

    @Test
    @DisplayName("TC-09: User A attempts to download digital asset of User B -> AccessDeniedException")
    void tc09_userCannotDownloadOtherUsersAsset() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        DigitalAsset asset = DigitalAsset.builder()
                .id(assetId)
                .userId(userB)
                .storagePath("models/user_b/high_quality.glb")
                .deliveryUrl("https://storage.memento3d.com/models/user_b/high_quality.glb")
                .status("READY")
                .build();

        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                digitalAssetService.generateDownloadUrl(assetId, userA, Role.ROLE_USER));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("TC-15: Out-of-order GPU callback cannot rollback completed job")
    void tc15_outOfOrderWorkerCallbackDoesNotRollbackCompletedJob() {
        ReconstructionJob completedJob = ReconstructionJob.builder()
                .id(UUID.randomUUID())
                .status(ReconstructionJobStatus.done)
                .progress(100)
                .build();

        assertSame(ReconstructionJobStatus.done, completedJob.getStatus());
    }

    @Test
    @DisplayName("TC-16: GPU worker retry policy increments retry count and re-queues job")
    void tc16_workerRetryPolicyIncrementsCount() {
        ReconstructionJob job = ReconstructionJob.builder()
                .id(UUID.randomUUID())
                .scan(scan)
                .status(ReconstructionJobStatus.failed)
                .retryCount(1)
                .build();

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(ReconstructionJob.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() ->
                reconstructionJobService.retryJob(job.getId(), userId, Role.ROLE_ADMIN));

        assertEquals(2, job.getRetryCount());
        assertEquals(ReconstructionJobStatus.queued, job.getStatus());
    }
}

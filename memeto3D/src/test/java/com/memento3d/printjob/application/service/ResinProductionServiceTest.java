package com.memento3d.printjob.application.service;

import com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderItemRepository;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterProvider;
import com.memento3d.printers.domain.model.PrinterStatus;
import com.memento3d.printers.domain.port.PrinterIntegrationPort;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printjob.api.request.*;
import com.memento3d.printjob.api.response.ResinProductionJobResponse;
import com.memento3d.models.domain.port.ModelConversionPort;
import com.memento3d.printjob.application.service.impl.ResinProductionServiceImpl;
import com.memento3d.printjob.domain.model.*;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.printjob.infrastructure.persistence.repository.ResinProductionDetailRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResinProductionServiceTest {

    @Mock private PrintJobRepository printJobRepository;
    @Mock private ResinProductionDetailRepository resinDetailRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private PrinterRepository printerRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private ModelConversionPort modelConversionPort;
    @Mock private PrinterIntegrationPort printerIntegrationPort;
    @Mock private OutboxPublisherService outboxPublisher;

    @InjectMocks
    private ResinProductionServiceImpl resinProductionService;

    private UUID orderItemId;
    private UUID orderId;
    private UUID printJobId;
    private UUID operatorId;
    private Order order;
    private OrderItem orderItem;
    private PrintJob printJob;
    private ResinProductionDetail detail;
    private Printer saturnPrinter;
    private Material resinMaterial;

    @BeforeEach
    void setUp() {
        orderItemId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        printJobId = UUID.randomUUID();
        operatorId = UUID.randomUUID();

        order = Order.builder()
                .id(orderId)
                .orderCode("ORD-RESIN-001")
                .status(OrderStatus.CONFIRMED)
                .totalPrice(BigDecimal.valueOf(1200000))
                .userId(UUID.randomUUID())
                .build();

        orderItem = OrderItem.builder()
                .id(orderItemId)
                .order(order)
                .productNameSnapshot("Memorial Keepsake Sculpture")
                .unitPrice(BigDecimal.valueOf(1200000))
                .quantity(1)
                .subtotal(BigDecimal.valueOf(1200000))
                .build();

        saturnPrinter = Printer.builder()
                .id(UUID.randomUUID())
                .name("Elegoo Saturn 4 Ultra 16K #01")
                .model("ELEGOO Saturn 4 Ultra 16K Mono LCD")
                .serialNumber("ELEGOO-S4U-16K-001")
                .provider(PrinterProvider.ELEGOO_SATURN_RESIN)
                .status(PrinterStatus.IDLE)
                .build();

        resinMaterial = Material.builder()
                .id(UUID.randomUUID())
                .name("Standard Photopolymer Resin 16K")
                .color("Space Grey")
                .build();

        printJob = PrintJob.builder()
                .id(printJobId)
                .jobNumber("RESIN-123456")
                .orderId(orderId)
                .orderItemId(orderItemId)
                .modelVersionId(orderItemId)
                .material(resinMaterial)
                .status(PrintJobStatus.PENDING_MODEL_PREPARATION)
                .printer(saturnPrinter)
                .build();

        detail = ResinProductionDetail.builder()
                .id(UUID.randomUUID())
                .printJobId(printJobId)
                .orderId(orderId)
                .orderItemId(orderItemId)
                .modelVersionId(orderItemId)
                .printerProfile("ELEGOO Saturn 4 Ultra 16K")
                .dimXMm(new BigDecimal("100.0"))
                .dimYMm(new BigDecimal("80.0"))
                .dimZMm(new BigDecimal("120.0"))
                .isMeshWatertight(true)
                .isHollowed(true)
                .hasDrainHoles(true)
                .build();
    }

    @Test
    @DisplayName("prepareProductionJob fails if order is not paid")
    void prepareProductionJob_OrderNotPaid_ThrowsException() {
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        PrepareResinProductionRequest request = new PrepareResinProductionRequest();
        request.setDimXMm(new BigDecimal("100.0"));
        request.setDimYMm(new BigDecimal("80.0"));
        request.setDimZMm(new BigDecimal("120.0"));

        assertThatThrownBy(() -> resinProductionService.prepareProductionJob(orderItemId, request, operatorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_NOT_READY_FOR_PRODUCTION");
    }

    @Test
    @DisplayName("prepareProductionJob fails if dimensions exceed Saturn 4 Ultra 16K build volume (218.88 x 122.88 x 220 mm)")
    void prepareProductionJob_DimensionsExceedBuildVolume_ThrowsException() {
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));

        PrepareResinProductionRequest request = new PrepareResinProductionRequest();
        request.setDimXMm(new BigDecimal("250.0")); // Exceeds 218.88
        request.setDimYMm(new BigDecimal("80.0"));
        request.setDimZMm(new BigDecimal("120.0"));

        assertThatThrownBy(() -> resinProductionService.prepareProductionJob(orderItemId, request, operatorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DIMENSIONS_EXCEED_BUILD_VOLUME");
    }

    @Test
    @DisplayName("prepareProductionJob succeeds and creates PrintJob and ResinProductionDetail")
    void prepareProductionJob_Success() {
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(orderItem));
        when(resinDetailRepository.findByOrderItemId(orderItemId)).thenReturn(Optional.empty());
        when(digitalAssetRepository.findByOrderItemId(orderItemId)).thenReturn(Optional.empty());
        when(materialRepository.findAll()).thenReturn(List.of(resinMaterial));
        when(modelConversionPort.convertGlbToStl(any(), any(), any(), any()))
                .thenReturn(ModelConversionPort.ConversionResult.pending("Waiting"));

        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> {
            PrintJob pj = i.getArgument(0);
            pj.setId(printJobId);
            return pj;
        });
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> {
            ResinProductionDetail d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        PrepareResinProductionRequest request = new PrepareResinProductionRequest();
        request.setDimXMm(new BigDecimal("100.0"));
        request.setDimYMm(new BigDecimal("80.0"));
        request.setDimZMm(new BigDecimal("120.0"));
        request.setIsMeshWatertight(true);
        request.setIsHollowed(true);
        request.setHasDrainHoles(true);

        ResinProductionJobResponse response = resinProductionService.prepareProductionJob(orderItemId, request, operatorId);

        assertThat(response).isNotNull();
        assertThat(response.getDimXMm()).isEqualTo(new BigDecimal("100.0"));
        verify(printJobRepository, atLeastOnce()).save(any(PrintJob.class));
        verify(resinDetailRepository).save(any(ResinProductionDetail.class));
    }

    @Test
    @DisplayName("recordSlicing fails if machine profile does not specify ELEGOO Saturn 4 Ultra 16K")
    void recordSlicing_InvalidMachineProfile_ThrowsException() {
        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));

        RecordSlicingRequest request = new RecordSlicingRequest();
        request.setSlicerVersion("CHITUBOX Basic v2.2.0");
        request.setPrinterProfile("ELEGOO Saturn 4 Ultra 12K"); // WRONG profile!
        request.setResinType("Elegoo Standard 8K");
        request.setResinColor("Space Grey");
        request.setSliceFileFormat("GOO");
        request.setSliceFileUrl("https://storage.memento3d.com/slices/job.goo");
        request.setEstimatedResinMl(new BigDecimal("45.5"));
        request.setEstimatedPrintTimeSeconds(7200);

        assertThatThrownBy(() -> resinProductionService.recordSlicing(printJobId, request, operatorId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_MACHINE_PROFILE");
    }

    @Test
    @DisplayName("recordSlicing succeeds with 16K profile, .goo format and updates status to READY_FOR_PRINTING")
    void recordSlicing_Success() {
        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> i.getArgument(0));

        RecordSlicingRequest request = new RecordSlicingRequest();
        request.setSlicerVersion("CHITUBOX Pro V2.0");
        request.setPrinterProfile("ELEGOO Saturn 4 Ultra 16K");
        request.setResinType("Elegoo Standard 16K Resin");
        request.setResinColor("Space Grey");
        request.setSliceFileFormat("GOO");
        request.setSliceFileUrl("https://storage.memento3d.com/slices/job-001.goo");
        request.setEstimatedResinMl(new BigDecimal("45.5"));
        request.setEstimatedPrintTimeSeconds(7200);

        ResinProductionJobResponse response = resinProductionService.recordSlicing(printJobId, request, operatorId);

        assertThat(response).isNotNull();
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.READY_FOR_PRINTING);
        assertThat(detail.getSliceFileFormat()).isEqualTo("GOO");
        assertThat(detail.getEstimatedResinMl()).isEqualTo(new BigDecimal("45.5"));
    }

    @Test
    @DisplayName("confirmHandoff does NOT advance status to PRINTING (remains READY_FOR_PRINTING)")
    void confirmHandoff_DoesNotAutoStartPrinting() {
        printJob.setStatus(PrintJobStatus.READY_FOR_PRINTING);
        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> i.getArgument(0));

        ConfirmHandoffRequest request = new ConfirmHandoffRequest();
        request.setHandoffMethod(ResinHandoffMethod.CHITU_MANAGER_LAN_MANUAL);

        ResinProductionJobResponse response = resinProductionService.confirmHandoff(printJobId, request, operatorId);

        assertThat(response).isNotNull();
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.READY_FOR_PRINTING); // Still READY, not PRINTING
        assertThat(detail.getHandoffMethod()).isEqualTo(ResinHandoffMethod.CHITU_MANAGER_LAN_MANUAL);
        assertThat(detail.getHandoffConfirmedBy()).isEqualTo(operatorId);
    }

    @Test
    @DisplayName("confirmStartPrinting moves status to PRINTING upon physical operator trigger")
    void confirmStartPrinting_MovesToPrinting() {
        printJob.setStatus(PrintJobStatus.READY_FOR_PRINTING);
        detail.setHandoffMethod(ResinHandoffMethod.USB_MANUAL);
        detail.setHandoffConfirmedAt(Instant.now());

        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> i.getArgument(0));

        ResinProductionJobResponse response = resinProductionService.confirmStartPrinting(printJobId, operatorId);

        assertThat(response).isNotNull();
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.PRINTING);
        assertThat(printJob.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("updatePostProcessing updates checklist and moves to QC_INSPECTION")
    void updatePostProcessing_Success() {
        printJob.setStatus(PrintJobStatus.POST_PROCESSING);
        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> i.getArgument(0));

        PostProcessingChecklistRequest request = new PostProcessingChecklistRequest();
        request.setWashCompleted(true);
        request.setSupportRemovalCompleted(true);
        request.setUvCureCompleted(true);
        request.setUvCureDurationMinutes(15);

        ResinProductionJobResponse response = resinProductionService.updatePostProcessing(printJobId, request, operatorId);

        assertThat(response).isNotNull();
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.QC_INSPECTION);
        assertThat(detail.getWashCompleted()).isTrue();
        assertThat(detail.getSupportRemovalCompleted()).isTrue();
        assertThat(detail.getUvCureCompleted()).isTrue();
    }

    @Test
    @DisplayName("recordQcResult PASS moves status to READY_FOR_DELIVERY")
    void recordQcResult_Pass_MovesToReadyForDelivery() {
        printJob.setStatus(PrintJobStatus.QC_INSPECTION);
        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> i.getArgument(0));

        RecordQcResultRequest request = new RecordQcResultRequest();
        request.setQcStatus("PASSED");
        request.setQcNotes("Perfect 16K details, no layer separation or surface blemishes");

        ResinProductionJobResponse response = resinProductionService.recordQcResult(printJobId, request, operatorId);

        assertThat(response).isNotNull();
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.READY_FOR_DELIVERY);
        assertThat(detail.getQcStatus()).isEqualTo("PASSED");
    }

    @Test
    @DisplayName("recordQcResult FAIL marks job FAILED with notes and photo")
    void recordQcResult_Fail_MarksJobFailed() {
        printJob.setStatus(PrintJobStatus.QC_INSPECTION);
        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> i.getArgument(0));
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> i.getArgument(0));

        RecordQcResultRequest request = new RecordQcResultRequest();
        request.setQcStatus("REJECTED");
        request.setFailureReasonCode("DELAMINATION");
        request.setQcNotes("Suction force pull-away due to inadequate drain hole");
        request.setFailurePhotoUrls("https://cloudinary.com/qc_fail_01.jpg");

        ResinProductionJobResponse response = resinProductionService.recordQcResult(printJobId, request, operatorId);

        assertThat(response).isNotNull();
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.FAILED);
        assertThat(detail.getQcStatus()).isEqualTo("REJECTED");
        assertThat(printJob.getFailureReason()).contains("DELAMINATION");
    }

    @Test
    @DisplayName("createReprint creates a new attempt linked to failed job without extra charges")
    void createReprint_KeepsHistoryAndNoExtraCharge() {
        printJob.setStatus(PrintJobStatus.FAILED);
        printJob.setJobNumber("RESIN-123456");
        detail.setAttemptNumber(1);

        when(printJobRepository.findById(printJobId)).thenReturn(Optional.of(printJob));
        when(resinDetailRepository.findByPrintJobId(printJobId)).thenReturn(Optional.of(detail));
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(i -> {
            PrintJob pj = i.getArgument(0);
            pj.setId(UUID.randomUUID());
            return pj;
        });
        when(resinDetailRepository.save(any(ResinProductionDetail.class))).thenAnswer(i -> i.getArgument(0));

        ResinProductionJobResponse response = resinProductionService.createReprint(printJobId, operatorId);

        assertThat(response).isNotNull();
        // Original job history is preserved, new job has reprintOfJobId
        verify(printJobRepository).save(argThat(pj -> printJobId.equals(pj.getReprintOfJobId())));
        // Verify no extra charge / order modification for customer
        verifyNoInteractions(orderRepository);
    }
}

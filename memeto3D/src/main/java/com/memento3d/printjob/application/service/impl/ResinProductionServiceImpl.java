package com.memento3d.printjob.application.service.impl;

import com.memento3d.assets.domain.model.DigitalAsset;
import com.memento3d.assets.infrastructure.persistence.repository.DigitalAssetRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.models.domain.port.ModelConversionPort;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderItemRepository;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.port.PrinterIntegrationPort;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printjob.api.request.*;
import com.memento3d.printjob.api.response.ResinProductionJobResponse;
import com.memento3d.printjob.application.service.ResinProductionService;
import com.memento3d.printjob.domain.model.*;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.printjob.infrastructure.persistence.repository.ResinProductionDetailRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.shared.outbox.application.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResinProductionServiceImpl implements ResinProductionService {

    private final PrintJobRepository printJobRepository;
    private final ResinProductionDetailRepository resinDetailRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final PrinterRepository printerRepository;
    private final MaterialRepository materialRepository;
    private final ModelConversionPort modelConversionPort;
    private final PrinterIntegrationPort printerIntegrationPort;
    private final OutboxPublisherService outboxPublisher;

    @Override
    @Transactional
    public ResinProductionJobResponse prepareProductionJob(UUID orderItemId, PrepareResinProductionRequest request, UUID operatorId) {
        log.info("Preparing resin production model for order item {} by operator {}", orderItemId, operatorId);

        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mục đơn hàng: " + orderItemId));

        Order order = orderItem.getOrder();
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORDER_NOT_READY_FOR_PRODUCTION",
                    "Đơn hàng chưa thanh toán hoặc chưa được xác nhận; không đủ điều kiện chuyển sản xuất.");
        }

        // Saturn 4 Ultra 16K build volume constraints: 218.88 x 122.88 x 220 mm
        if ((request.getDimXMm() != null && request.getDimXMm().compareTo(new BigDecimal("218.88")) > 0) ||
            (request.getDimYMm() != null && request.getDimYMm().compareTo(new BigDecimal("122.88")) > 0) ||
            (request.getDimZMm() != null && request.getDimZMm().compareTo(new BigDecimal("220.00")) > 0)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DIMENSIONS_EXCEED_BUILD_VOLUME",
                    "Kích thước vượt quá khổ in tối đa của ELEGOO Saturn 4 Ultra 16K (218.88 x 122.88 x 220 mm)");
        }

        // Lấy DigitalAsset gốc (GLB)
        DigitalAsset glbAsset = digitalAssetRepository.findByOrderItemId(orderItemId)
                .stream()
                .filter(a -> "GLB".equalsIgnoreCase(a.getFileFormat()))
                .findFirst()
                .orElse(null);

        String glbUrl = glbAsset != null ? glbAsset.getDeliveryUrl() : "";
        UUID modelVersionId = orderItem.getId();

        // Chuyển đổi GLB sang STL hình học (nếu có bytes hoặc converter chạy nền)
        ModelConversionPort.ModelDimensions dims = new ModelConversionPort.ModelDimensions(
                request.getDimXMm(),
                request.getDimYMm(),
                request.getDimZMm(),
                request.getScale()
        );
        ModelConversionPort.ConversionResult convResult = modelConversionPort.convertGlbToStl(
                modelVersionId,
                new byte[0], // Handled asynchronously or via cloud asset
                "mm",
                dims
        );

        // Chuẩn bị hoặc tìm PrintJob
        Optional<ResinProductionDetail> existingDetailOpt = resinDetailRepository.findByOrderItemId(orderItemId);
        PrintJob printJob;
        ResinProductionDetail detail;

        if (existingDetailOpt.isPresent()) {
            ResinProductionDetail existing = existingDetailOpt.get();
            detail = existing;
            UUID existingDetailId = existing.getId();
            printJob = printJobRepository.findById(existing.getPrintJobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob cho detail: " + existingDetailId));
        } else {
            // Lấy material mặc định (Resin)
            Material resinMat = materialRepository.findAll().stream()
                    .filter(m -> m.getName() != null && m.getName().toLowerCase().contains("resin"))
                    .findFirst()
                    .orElseGet(() -> materialRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new IllegalStateException("Hệ thống chưa cấu hình vật liệu in (Resin)")));

            printJob = PrintJob.builder()
                    .jobNumber("RESIN-" + System.currentTimeMillis() % 1000000)
                    .orderId(order.getId())
                    .orderItemId(orderItemId)
                    .modelVersionId(modelVersionId)
                    .material(resinMat)
                    .status(PrintJobStatus.AWAITING_SLICING)
                    .estimatedGram(BigDecimal.valueOf(80.0))
                    .estimatedDurationSeconds(7200)
                    .createdBy(operatorId)
                    .build();
            printJob = printJobRepository.save(printJob);

            detail = ResinProductionDetail.builder()
                    .printJobId(printJob.getId())
                    .orderId(order.getId())
                    .orderItemId(orderItemId)
                    .modelVersionId(modelVersionId)
                    .build();
        }

        // Cập nhật thông số hình học
        detail.setDimXMm(request.getDimXMm());
        detail.setDimYMm(request.getDimYMm());
        detail.setDimZMm(request.getDimZMm());
        detail.setIsMeshWatertight(request.getIsMeshWatertight());
        detail.setIsHollowed(request.getIsHollowed());
        detail.setHasDrainHoles(request.getHasDrainHoles());
        detail.setSupportDensity(request.getSupportDensity());
        detail.setStlDeliveryUrl(glbUrl.replace(".glb", ".stl")); // Placeholder until STL artifact confirmed

        printJob.setStatus(PrintJobStatus.AWAITING_SLICING);
        printJobRepository.save(printJob);
        detail = resinDetailRepository.save(detail);

        return mapToResponse(printJob, detail, glbUrl);
    }

    @Override
    @Transactional
    public ResinProductionJobResponse recordSlicing(UUID printJobId, RecordSlicingRequest request, UUID operatorId) {
        log.info("Recording CHITUBOX slicing profile for job {} by operator {}", printJobId, operatorId);

        PrintJob printJob = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob: " + printJobId));

        ResinProductionDetail detail = resinDetailRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ResinProductionDetail cho job: " + printJobId));

        // Kiểm tra đúng profile máy in mục tiêu: Phải là ELEGOO Saturn 4 Ultra 16K
        if (request.getPrinterProfile() != null && !request.getPrinterProfile().contains("16K")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MACHINE_PROFILE",
                    "Chỉ chấp nhận profile máy ELEGOO Saturn 4 Ultra 16K (không dùng profile 12K hoặc máy khác)");
        }

        detail.setSlicerName(request.getSlicerName() != null ? request.getSlicerName() : "CHITUBOX");
        detail.setSlicerVersion(request.getSlicerVersion());
        detail.setPrinterProfile("ELEGOO Saturn 4 Ultra 16K");
        detail.setSliceFileFormat(request.getSliceFileFormat().toUpperCase());
        detail.setSliceFileUrl(request.getSliceFileUrl());
        detail.setSliceFileSha256(request.getSliceFileSha256());
        detail.setPreviewImageUrl(request.getPreviewImageUrl());

        // Resin thông số
        detail.setResinType(request.getResinType());
        detail.setResinColor(request.getResinColor());
        detail.setIsCustomPainted(Boolean.TRUE.equals(request.getIsCustomPainted()));
        detail.setEstimatedResinMl(request.getEstimatedResinMl());
        detail.setEstimatedPrintTimeSeconds(request.getEstimatedPrintTimeSeconds());

        printJob.setEstimatedGram(request.getEstimatedResinMl().multiply(BigDecimal.valueOf(1.1))); // Density approx 1.1 g/ml
        printJob.setEstimatedDurationSeconds(request.getEstimatedPrintTimeSeconds());
        printJob.setStatus(PrintJobStatus.READY_FOR_PRINTING);

        printJobRepository.save(printJob);
        detail = resinDetailRepository.save(detail);

        return mapToResponse(printJob, detail, null);
    }

    @Override
    @Transactional
    public ResinProductionJobResponse confirmHandoff(UUID printJobId, ConfirmHandoffRequest request, UUID operatorId) {
        log.info("Confirming file handoff for job {} via method {} by operator {}", printJobId, request.getHandoffMethod(), operatorId);

        PrintJob printJob = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob: " + printJobId));

        ResinProductionDetail detail = resinDetailRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ResinProductionDetail cho job: " + printJobId));

        if (request.getPrinterId() != null) {
            Printer printer = printerRepository.findById(request.getPrinterId()).orElse(null);
            if (printer != null) {
                printJob.setPrinter(printer);
            }
        }

        detail.setHandoffMethod(request.getHandoffMethod());
        detail.setHandoffConfirmedBy(operatorId);
        detail.setHandoffConfirmedAt(Instant.now());

        // Gọi port bàn giao để ghi nhận log
        printerIntegrationPort.handoffFile(printJobId, detail.getSliceFileUrl(), request.getHandoffMethod(), operatorId);

        // Lưu ý: Bàn giao file KHÔNG chuyển sang PRINTING mà giữ nguyên READY_FOR_PRINTING
        printJobRepository.save(printJob);
        detail = resinDetailRepository.save(detail);

        return mapToResponse(printJob, detail, null);
    }

    @Override
    @Transactional
    public ResinProductionJobResponse confirmStartPrinting(UUID printJobId, UUID operatorId) {
        log.info("Operator {} confirmed printing started physically on machine for job {}", operatorId, printJobId);

        PrintJob printJob = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob: " + printJobId));

        ResinProductionDetail detail = resinDetailRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ResinProductionDetail cho job: " + printJobId));

        Instant now = Instant.now();
        detail.setOperatorConfirmedPrintingAt(now);
        printJob.setStartedAt(now);
        printJob.setStatus(PrintJobStatus.PRINTING);

        printJobRepository.save(printJob);
        detail = resinDetailRepository.save(detail);

        outboxPublisher.publish("PRINT_JOB", printJob.getId().toString(), "RESIN_PRINTING_STARTED", Map.of(
                "jobId", printJob.getId(),
                "orderId", printJob.getOrderId() != null ? printJob.getOrderId() : "",
                "status", "PRINTING",
                "machine", "ELEGOO Saturn 4 Ultra 16K"
        ));

        return mapToResponse(printJob, detail, null);
    }

    @Override
    @Transactional
    public ResinProductionJobResponse updatePostProcessing(UUID printJobId, PostProcessingChecklistRequest request, UUID operatorId) {
        log.info("Updating post-processing checklist for job {} by operator {}", printJobId, operatorId);

        PrintJob printJob = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob: " + printJobId));

        ResinProductionDetail detail = resinDetailRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ResinProductionDetail cho job: " + printJobId));

        detail.setWashCompleted(Boolean.TRUE.equals(request.getWashCompleted()));
        detail.setSupportRemovalCompleted(Boolean.TRUE.equals(request.getSupportRemovalCompleted()));
        detail.setUvCureCompleted(Boolean.TRUE.equals(request.getUvCureCompleted()));
        detail.setUvCureDurationMinutes(request.getUvCureDurationMinutes());
        detail.setSurfaceFinishingCompleted(Boolean.TRUE.equals(request.getSurfaceFinishingCompleted()));

        if (Boolean.TRUE.equals(request.getSurfaceFinishingCompleted())) {
            printJob.setStatus(PrintJobStatus.SURFACE_FINISHING);
        } else if (Boolean.TRUE.equals(request.getUvCureCompleted())) {
            printJob.setStatus(PrintJobStatus.QC_INSPECTION);
        } else {
            printJob.setStatus(PrintJobStatus.POST_PROCESSING);
        }

        printJobRepository.save(printJob);
        detail = resinDetailRepository.save(detail);

        return mapToResponse(printJob, detail, null);
    }

    @Override
    @Transactional
    public ResinProductionJobResponse recordQcResult(UUID printJobId, RecordQcResultRequest request, UUID inspectorId) {
        log.info("Recording QC result for job {} by inspector {}: status={}", printJobId, inspectorId, request.getQcStatus());

        PrintJob printJob = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob: " + printJobId));

        ResinProductionDetail detail = resinDetailRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ResinProductionDetail cho job: " + printJobId));

        boolean isPassed = "PASSED".equalsIgnoreCase(request.getQcStatus());
        detail.setQcStatus(isPassed ? "PASSED" : "REJECTED");
        detail.setQcNotes(request.getQcNotes());

        if (isPassed) {
            printJob.setStatus(PrintJobStatus.READY_FOR_DELIVERY);
            printJob.setEndedAt(Instant.now());
            outboxPublisher.publish("PRINT_JOB", printJob.getId().toString(), "RESIN_QC_PASSED", Map.of(
                    "jobId", printJob.getId(),
                    "status", "READY_FOR_DELIVERY"
            ));
        } else {
            printJob.setStatus(PrintJobStatus.FAILED);
            printJob.setFailureReason(request.getFailureReasonCode() + ": " + request.getQcNotes());
            detail.setFailurePhotoUrls(request.getFailurePhotoUrls());
            outboxPublisher.publish("PRINT_JOB", printJob.getId().toString(), "RESIN_PRINT_FAILED", Map.of(
                    "jobId", printJob.getId(),
                    "reason", request.getFailureReasonCode() != null ? request.getFailureReasonCode() : "QC_FAILED"
            ));
        }

        printJobRepository.save(printJob);
        detail = resinDetailRepository.save(detail);

        return mapToResponse(printJob, detail, null);
    }

    @Override
    @Transactional
    public ResinProductionJobResponse createReprint(UUID failedPrintJobId, UUID operatorId) {
        log.info("Creating reprint attempt for failed job {} by operator {}", failedPrintJobId, operatorId);

        PrintJob failedJob = printJobRepository.findById(failedPrintJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob gốc: " + failedPrintJobId));

        ResinProductionDetail failedDetail = resinDetailRepository.findByPrintJobId(failedPrintJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ResinDetail gốc: " + failedPrintJobId));

        int nextAttempt = (failedDetail.getAttemptNumber() != null ? failedDetail.getAttemptNumber() : 1) + 1;

        // Tạo print job mới giữ nguyên liên kết lần in trước (reprintOfJobId)
        PrintJob newJob = PrintJob.builder()
                .jobNumber("RESIN-REPRINT-" + failedJob.getJobNumber() + "-ATTEMPT-" + nextAttempt)
                .orderId(failedJob.getOrderId())
                .orderItemId(failedJob.getOrderItemId())
                .modelVersionId(failedJob.getModelVersionId())
                .printer(failedJob.getPrinter())
                .material(failedJob.getMaterial())
                .status(PrintJobStatus.READY_FOR_PRINTING) // Dùng lại file slice đã chuẩn bị
                .estimatedGram(failedJob.getEstimatedGram())
                .estimatedDurationSeconds(failedJob.getEstimatedDurationSeconds())
                .reprintOfJobId(failedJob.getId())
                .createdBy(operatorId)
                .build();
        newJob = printJobRepository.save(newJob);

        ResinProductionDetail newDetail = ResinProductionDetail.builder()
                .printJobId(newJob.getId())
                .orderId(failedDetail.getOrderId())
                .orderItemId(failedDetail.getOrderItemId())
                .modelVersionId(failedDetail.getModelVersionId())
                .stlAssetId(failedDetail.getStlAssetId())
                .stlDeliveryUrl(failedDetail.getStlDeliveryUrl())
                .dimXMm(failedDetail.getDimXMm())
                .dimYMm(failedDetail.getDimYMm())
                .dimZMm(failedDetail.getDimZMm())
                .isMeshWatertight(failedDetail.getIsMeshWatertight())
                .isHollowed(failedDetail.getIsHollowed())
                .hasDrainHoles(failedDetail.getHasDrainHoles())
                .supportDensity(failedDetail.getSupportDensity())
                .slicerName(failedDetail.getSlicerName())
                .slicerVersion(failedDetail.getSlicerVersion())
                .printerProfile(failedDetail.getPrinterProfile())
                .sliceFileFormat(failedDetail.getSliceFileFormat())
                .sliceFileUrl(failedDetail.getSliceFileUrl())
                .sliceFileSha256(failedDetail.getSliceFileSha256())
                .previewImageUrl(failedDetail.getPreviewImageUrl())
                .resinType(failedDetail.getResinType())
                .resinColor(failedDetail.getResinColor())
                .isCustomPainted(failedDetail.getIsCustomPainted())
                .estimatedResinMl(failedDetail.getEstimatedResinMl())
                .estimatedPrintTimeSeconds(failedDetail.getEstimatedPrintTimeSeconds())
                .attemptNumber(nextAttempt)
                .build();
        newDetail = resinDetailRepository.save(newDetail);

        return mapToResponse(newJob, newDetail, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ResinProductionJobResponse getJobDetails(UUID printJobId) {
        PrintJob job = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy PrintJob: " + printJobId));
        ResinProductionDetail detail = resinDetailRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ResinProductionDetail cho job: " + printJobId));
        return mapToResponse(job, detail, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResinProductionJobResponse> listResinJobs(int page, int limit) {
        Page<ResinProductionDetail> detailsPage = resinDetailRepository.findAll(PageRequest.of(Math.max(0, page - 1), limit));
        List<ResinProductionJobResponse> responses = new ArrayList<>();
        for (ResinProductionDetail detail : detailsPage.getContent()) {
            printJobRepository.findById(detail.getPrintJobId())
                    .ifPresent(job -> responses.add(mapToResponse(job, detail, null)));
        }
        return responses;
    }

    private ResinProductionJobResponse mapToResponse(PrintJob job, ResinProductionDetail detail, String glbUrlOverride) {
        String glbUrl = glbUrlOverride;
        if (glbUrl == null && detail.getOrderItemId() != null) {
            glbUrl = digitalAssetRepository.findByOrderItemId(detail.getOrderItemId())
                    .stream()
                    .filter(a -> "GLB".equalsIgnoreCase(a.getFileFormat()))
                    .findFirst()
                    .map(DigitalAsset::getDeliveryUrl)
                    .orElse("");
        }

        return ResinProductionJobResponse.builder()
                .printJobId(job.getId())
                .jobNumber(job.getJobNumber())
                .orderId(job.getOrderId())
                .orderItemId(job.getOrderItemId())
                .modelVersionId(job.getModelVersionId())
                .status(job.getStatus())
                .statusDescription(getStatusDescription(job.getStatus()))
                .glbViewerUrl(glbUrl)
                .stlGeometryUrl(detail.getStlDeliveryUrl())
                .sliceFileUrl(detail.getSliceFileUrl())
                .sliceFileFormat(detail.getSliceFileFormat())
                .previewImageUrl(detail.getPreviewImageUrl())
                .dimXMm(detail.getDimXMm())
                .dimYMm(detail.getDimYMm())
                .dimZMm(detail.getDimZMm())
                .isMeshWatertight(detail.getIsMeshWatertight())
                .isHollowed(detail.getIsHollowed())
                .hasDrainHoles(detail.getHasDrainHoles())
                .supportDensity(detail.getSupportDensity())
                .slicerName(detail.getSlicerName())
                .slicerVersion(detail.getSlicerVersion())
                .targetPrinterProfile(detail.getPrinterProfile())
                .resinType(detail.getResinType())
                .resinColor(detail.getResinColor())
                .isCustomPainted(detail.getIsCustomPainted())
                .estimatedResinMl(detail.getEstimatedResinMl())
                .estimatedPrintTimeSeconds(detail.getEstimatedPrintTimeSeconds())
                .handoffMethod(detail.getHandoffMethod())
                .handoffConfirmedAt(detail.getHandoffConfirmedAt())
                .printingStartedAt(detail.getOperatorConfirmedPrintingAt())
                .washCompleted(detail.getWashCompleted())
                .supportRemovalCompleted(detail.getSupportRemovalCompleted())
                .uvCureCompleted(detail.getUvCureCompleted())
                .uvCureDurationMinutes(detail.getUvCureDurationMinutes())
                .surfaceFinishingCompleted(detail.getSurfaceFinishingCompleted())
                .qcStatus(detail.getQcStatus())
                .qcNotes(detail.getQcNotes())
                .attemptNumber(detail.getAttemptNumber())
                .reprintOfJobId(job.getReprintOfJobId())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private String getStatusDescription(PrintJobStatus status) {
        if (status == null) return "Chưa xác định";
        return switch (status) {
            case PENDING_MODEL_PREPARATION -> "Chờ chuẩn bị mô hình hình học (STL)";
            case AWAITING_SLICING -> "Chờ cắt lớp trong phần mềm CHITUBOX";
            case READY_FOR_PRINTING -> "Sẵn sàng sản xuất (File cắt lớp đã nạp)";
            case PRINTING -> "Đang in thực tế trên máy ELEGOO Saturn 4 Ultra 16K";
            case POST_PROCESSING -> "Hậu xử lý (Rửa cồn / Bóc Support / Đóng rắn UV)";
            case SURFACE_FINISHING -> "Hoàn thiện bề mặt và sơn màu mỹ thuật";
            case QC_INSPECTION -> "Kiểm tra chất lượng thành phẩm (QC)";
            case READY_FOR_DELIVERY -> "Đạt chuẩn chất lượng, sẵn sàng đóng gói giao hàng";
            case COMPLETED -> "Sản xuất hoàn tất";
            case FAILED -> "In thất bại / Cần tạo lần in lại";
            case CANCELLED -> "Đã hủy tác vụ";
            default -> status.name();
        };
    }
}

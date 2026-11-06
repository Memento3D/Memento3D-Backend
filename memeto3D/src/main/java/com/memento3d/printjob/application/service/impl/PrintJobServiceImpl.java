package com.memento3d.printjob.application.service.impl;

import com.memento3d.inventory.domain.model.InventoryBalance;
import com.memento3d.inventory.domain.model.InventoryTransaction;
import com.memento3d.inventory.domain.model.InventoryTransactionType;
import com.memento3d.inventory.domain.model.StockReservation;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockReservationRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterStatus;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printjob.api.request.ConsumptionReconciliationRequest;
import com.memento3d.printjob.api.request.CreatePrintJobRequest;
import com.memento3d.printjob.api.request.MaterialIssueRequest;
import com.memento3d.printjob.api.request.ReserveStockRequest;
import com.memento3d.printjob.api.response.PrintJobConsumptionResponse;
import com.memento3d.printjob.api.response.PrintJobResponse;
import com.memento3d.printjob.api.response.StockReservationResponse;
import com.memento3d.printjob.application.service.PrintJobService;
import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobConsumption;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobConsumptionRepository;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintJobServiceImpl implements PrintJobService {

    private final PrintJobRepository printJobRepository;
    private final StockReservationRepository reservationRepository;
    private final PrintJobConsumptionRepository consumptionRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final PrinterRepository printerRepository;
    private final com.memento3d.orders.infrastructure.persistence.repository.OrderRepository orderRepository;
    private final com.memento3d.modelreview.infrastructure.persistence.repository.ModelVersionRepository modelVersionRepository;
    private final com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;
    private final com.memento3d.customization.application.service.PrintDesignService printDesignService;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional
    public PrintJobResponse createPrintJob(CreatePrintJobRequest request, UUID actorId) {
        printDesignService.validateVersionBinding(request.getModelVersionId(),request.getOrderId());
        Material material = materialRepository.findById(request.getMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("Material not found with id: " + request.getMaterialId()));

        Printer printer = null;
        if (request.getPrinterId() != null) {
            printer = printerRepository.findById(request.getPrinterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Printer not found with id: " + request.getPrinterId()));
        }

        // Validate order linkage and model approval if order is attached
        if(request.getOrderItemId()!=null && request.getOrderId()==null)
            throw new BusinessException(HttpStatus.BAD_REQUEST,"ORDER_REQUIRED","An order item requires its order ID");
        if (request.getOrderId() != null) {
            com.memento3d.orders.domain.model.Order order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

            if(request.getOrderItemId()!=null && order.getItems().stream().noneMatch(i->i.getId().equals(request.getOrderItemId())))
                throw new BusinessException(HttpStatus.BAD_REQUEST,"ORDER_ITEM_MISMATCH","Order item does not belong to this order");
            if(printDesignService.isCustom(order.getId())) {
                entityManager.lock(order,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                if(order.getStatus()!=com.memento3d.orders.domain.model.OrderStatus.PROCESSING
                    || !(order.getPaymentStatus()==com.memento3d.payment.domain.model.PaymentStatus.PAID || order.getPaymentStatus()==com.memento3d.payment.domain.model.PaymentStatus.SUCCESS)
                    || !"ACCEPTED".equals(order.getFeasibilityStatus()))
                    throw new BusinessException(HttpStatus.CONFLICT,"CUSTOM_ORDER_NOT_PRINTABLE","Custom order must be accepted, paid and PROCESSING");
                request.setModelVersionId(printDesignService.requirePrintable(order.getId(),request.getOrderItemId(),request.getModelVersionId()));
            }

            if (request.getOrderItemId() != null) {
                if (printJobRepository.existsByOrderItemIdAndStatusNot(request.getOrderItemId(), PrintJobStatus.CANCELLED)) {
                    throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_PRINT_JOB", "A print job already exists for this order item.");
                }
            }

            // Verify Model Approval
            if (request.getModelVersionId() != null) {
                com.memento3d.modelreview.domain.model.ModelVersion version = modelVersionRepository.findById(request.getModelVersionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Model version not found: " + request.getModelVersionId()));
                if (!version.isApproved()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_NOT_APPROVED",
                            "Model version " + version.getVersionNumber() + " must be APPROVED before physical printing.");
                }
                if(printDesignService.isCustom(order.getId())) {version.setStatus("LOCKED");modelVersionRepository.save(version);}
            } else if (order.getScanId() != null) {
                java.util.Optional<com.memento3d.modelreview.domain.model.ModelVersion> latestApproved =
                        modelVersionRepository.findFirstByScan_IdOrderByVersionNumberDesc(order.getScanId());
                if (latestApproved.isEmpty() || !latestApproved.get().isApproved()) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "MODEL_NOT_APPROVED",
                            "Cannot create PrintJob: 3D model version must be APPROVED by customer before physical manufacturing.");
                }
            }
        }

        String jobNumber = "JOB-" + Instant.now().toEpochMilli();
        PrintJob job = PrintJob.builder()
                .jobNumber(jobNumber)
                .orderId(request.getOrderId())
                .orderItemId(request.getOrderItemId())
                .modelVersionId(request.getModelVersionId())
                .isOutsource(false)
                .printer(printer)
                .material(material)
                .estimatedGram(request.getEstimatedGram())
                .estimatedDurationSeconds(request.getEstimatedDurationSeconds())
                .status(PrintJobStatus.CREATED)
                .createdBy(actorId)
                .build();

        PrintJob saved = printJobRepository.save(job);
        log.info("Created Print Job ID={}, JobNumber={}, Outsource={}", saved.getId(), saved.getJobNumber(), saved.isOutsource());

        outboxPublisher.publish(
                "PRINT_JOB",
                saved.getId().toString(),
                "PRINT_JOB_CREATED",
                java.util.Map.of(
                        "jobId", saved.getId(),
                        "jobNumber", saved.getJobNumber(),
                        "orderId", saved.getOrderId() != null ? saved.getOrderId() : "",
                        "isOutsource", saved.isOutsource()
                )
        );

        return PrintJobResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrintJobResponse> listPrintJobs(PrintJobStatus status, UUID printerId, UUID materialId, Pageable pageable) {
        if (status == null && printerId == null && materialId == null) {
            return printJobRepository.findAll(pageable).map(PrintJobResponse::fromEntity);
        }
        return printJobRepository.findAllWithFilters(status, printerId, materialId, pageable)
                .map(PrintJobResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PrintJobResponse getPrintJob(UUID id) {
        PrintJob job = printJobRepository.findWithDetailsById(id)
                .or(() -> printJobRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Print Job not found with id: " + id));
        return PrintJobResponse.fromEntity(job);
    }

    @Override
    @Transactional
    public StockReservationResponse reserveStock(UUID printJobId, ReserveStockRequest request, UUID actorId) {
        PrintJob job = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Print Job not found with id: " + printJobId));

        if (job.getStatus() == PrintJobStatus.PRINTING || job.getStatus() == PrintJobStatus.COMPLETED ||
                job.getStatus() == PrintJobStatus.FAILED || job.getStatus() == PrintJobStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_JOB_STATE",
                    "Cannot reserve material for job with status: " + job.getStatus());
        }

        if (reservationRepository.existsByPrintJobIdAndStatus(printJobId, "ACTIVE")) {
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_RESERVATION",
                    "Print Job already has an active stock reservation");
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.getWarehouseId()));

        Material material = job.getMaterial();
        BigDecimal reservedQty = request.getReservedGram();

        // Lock balance row using PESSIMISTIC_WRITE
        InventoryBalance balance = balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouse.getId(), material.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                        "Material not available in specified warehouse"));

        if (balance.getAvailableGram().compareTo(reservedQty) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Insufficient available stock: Requested " + reservedQty + "g, Available " + balance.getAvailableGram() + "g");
        }

        BigDecimal beforeOnHand = balance.getOnHandGram();
        BigDecimal beforeReserved = balance.getReservedGram();
        BigDecimal afterReserved = beforeReserved.add(reservedQty);

        balance.setReservedGram(afterReserved);
        balanceRepository.save(balance);

        StockReservation reservation = StockReservation.builder()
                .printJob(job)
                .warehouse(warehouse)
                .material(material)
                .reservedGram(reservedQty)
                .status("ACTIVE")
                .createdBy(actorId)
                .build();
        StockReservation savedReservation = reservationRepository.save(reservation);

        job.setStatus(PrintJobStatus.RESERVED);
        PrintJob savedJob = printJobRepository.save(job);
        publishPrintJobEvent(savedJob != null ? savedJob : job, "PRINT_JOB_UPDATED", java.util.Map.of("action", "RESERVED"));

        // Immutable transaction
        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(InventoryTransactionType.RESERVATION_HOLD)
                .warehouse(warehouse)
                .material(material)
                .deltaOnHandGram(BigDecimal.ZERO)
                .deltaReservedGram(reservedQty)
                .beforeOnHandGram(beforeOnHand)
                .afterOnHandGram(beforeOnHand)
                .beforeReservedGram(beforeReserved)
                .afterReservedGram(afterReserved)
                .printJobId(job.getId())
                .referenceId(job.getJobNumber())
                .performedBy(actorId)
                .build();
        transactionRepository.save(tx);

        log.info("Stock reserved: Job={}, Material={}, Qty={}g, Warehouse={}",
                job.getJobNumber(), material.getSku(), reservedQty, warehouse.getCode());

        return StockReservationResponse.fromEntity(savedReservation);
    }

    @Override
    @Transactional(readOnly = true)
    public StockReservationResponse getReservation(UUID printJobId) {
        StockReservation reservation = reservationRepository.findByPrintJobIdAndStatus(printJobId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("No active reservation found for Print Job id: " + printJobId));
        return StockReservationResponse.fromEntity(reservation);
    }

    @Override
    @Transactional
    public void releaseReservation(UUID printJobId, UUID actorId) {
        PrintJob job = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Print Job not found with id: " + printJobId));

        StockReservation reservation = reservationRepository.findByPrintJobIdAndStatus(printJobId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("No active reservation found for Print Job id: " + printJobId));

        InventoryBalance balance = balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(
                reservation.getWarehouse().getId(), reservation.getMaterial().getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "BALANCE_NOT_FOUND", "Inventory balance missing"));

        BigDecimal beforeOnHand = balance.getOnHandGram();
        BigDecimal beforeReserved = balance.getReservedGram();
        BigDecimal afterReserved = beforeReserved.subtract(reservation.getReservedGram()).max(BigDecimal.ZERO);

        balance.setReservedGram(afterReserved);
        balanceRepository.save(balance);

        reservation.setStatus("RELEASED");
        reservationRepository.save(reservation);

        job.setStatus(PrintJobStatus.CREATED);
        printJobRepository.save(job);

        // Immutable transaction
        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(InventoryTransactionType.RESERVATION_RELEASE)
                .warehouse(reservation.getWarehouse())
                .material(reservation.getMaterial())
                .deltaOnHandGram(BigDecimal.ZERO)
                .deltaReservedGram(reservation.getReservedGram().negate())
                .beforeOnHandGram(beforeOnHand)
                .afterOnHandGram(beforeOnHand)
                .beforeReservedGram(beforeReserved)
                .afterReservedGram(afterReserved)
                .printJobId(job.getId())
                .referenceId(job.getJobNumber())
                .performedBy(actorId)
                .build();
        transactionRepository.save(tx);

        log.info("Reservation released for Job ID={}", job.getId());
    }

    @Override
    @Transactional
    public PrintJobConsumptionResponse issueMaterial(UUID printJobId, MaterialIssueRequest request, UUID actorId) {
        PrintJob job = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Print Job not found with id: " + printJobId));

        if (job.getStatus() == PrintJobStatus.PRINTING || job.getStatus() == PrintJobStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_JOB_STATE",
                    "Cannot issue material for job with status: " + job.getStatus());
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.getWarehouseId()));

        Material material = job.getMaterial();
        BigDecimal issuedQty = request.getIssuedGram();

        // Lock balance row using PESSIMISTIC_WRITE
        InventoryBalance balance = balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouse.getId(), material.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Material balance missing"));

        // Check if job had an active reservation
        Optional<StockReservation> activeReservationOpt = reservationRepository.findByPrintJobIdAndStatus(printJobId, "ACTIVE");
        BigDecimal beforeOnHand = balance.getOnHandGram();
        BigDecimal beforeReserved = balance.getReservedGram();

        BigDecimal afterOnHand = beforeOnHand.subtract(issuedQty);
        if (afterOnHand.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Cannot issue " + issuedQty + "g: on-hand balance is only " + beforeOnHand + "g");
        }

        BigDecimal afterReserved = beforeReserved;
        if (activeReservationOpt.isPresent()) {
            StockReservation reservation = activeReservationOpt.get();
            afterReserved = beforeReserved.subtract(reservation.getReservedGram()).max(BigDecimal.ZERO);
            reservation.setStatus("FULFILLED");
            reservationRepository.save(reservation);
        }

        balance.setOnHandGram(afterOnHand);
        balance.setReservedGram(afterReserved);
        balanceRepository.save(balance);

        PrintJobConsumption consumption = consumptionRepository.findByPrintJobId(printJobId)
                .orElseGet(() -> PrintJobConsumption.builder()
                        .printJob(job)
                        .warehouse(warehouse)
                        .material(material)
                        .issuedGram(BigDecimal.ZERO)
                        .build());

        consumption.setIssuedGram(consumption.getIssuedGram().add(issuedQty));
        consumption.setNote(request.getNote());
        PrintJobConsumption savedConsumption = consumptionRepository.save(consumption);

        job.setStatus(PrintJobStatus.ISSUED);
        PrintJob savedJob = printJobRepository.save(job);
        publishPrintJobEvent(savedJob != null ? savedJob : job, "PRINT_JOB_UPDATED", java.util.Map.of("action", "ISSUED"));

        // Immutable transaction
        InventoryTransaction tx = InventoryTransaction.builder()
                .transactionType(InventoryTransactionType.PRINT_ISSUE)
                .warehouse(warehouse)
                .material(material)
                .deltaOnHandGram(issuedQty.negate())
                .deltaReservedGram(beforeReserved.subtract(afterReserved).negate())
                .beforeOnHandGram(beforeOnHand)
                .afterOnHandGram(afterOnHand)
                .beforeReservedGram(beforeReserved)
                .afterReservedGram(afterReserved)
                .printJobId(job.getId())
                .referenceId(job.getJobNumber())
                .performedBy(actorId)
                .build();
        transactionRepository.save(tx);

        log.info("Material issued for Print Job: Job={}, Qty={}g, Warehouse={}",
                job.getJobNumber(), issuedQty, warehouse.getCode());

        return PrintJobConsumptionResponse.fromEntity(savedConsumption);
    }

    @Override
    @Transactional
    public PrintJobResponse startPrint(UUID printJobId) {
        PrintJob job = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Print Job not found with id: " + printJobId));

        if (job.getStatus() != PrintJobStatus.ISSUED) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATERIAL_NOT_ISSUED",
                    "Material must be issued before starting print job (current: " + job.getStatus() + ")");
        }

        if (job.getPrinter() != null) {
            Printer printer = job.getPrinter();
            if (!printer.isAvailableForPrint()) {
                throw new BusinessException(HttpStatus.CONFLICT, "PRINTER_NOT_AVAILABLE",
                        "Assigned printer status is " + printer.getStatus() + ", required IDLE");
            }
            printer.setStatus(PrinterStatus.PRINTING);
            printerRepository.save(printer);
        }

        job.setStatus(PrintJobStatus.PRINTING);
        job.setStartedAt(Instant.now());
        PrintJob saved = printJobRepository.save(job);
        publishPrintJobEvent(saved != null ? saved : job, "PRINT_JOB_STARTED", java.util.Map.of("progress", 0));
        log.info("Print Job started ID={}, JobNumber={}", saved.getId(), saved.getJobNumber());
        return PrintJobResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public PrintJobConsumptionResponse reconcileConsumption(
            UUID printJobId,
            ConsumptionReconciliationRequest request,
            UUID actorId
    ) {
        PrintJob job = printJobRepository.findById(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Print Job not found with id: " + printJobId));

        PrintJobConsumption consumption = consumptionRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumption record not found for job id: " + printJobId));

        BigDecimal issued = consumption.getIssuedGram();
        BigDecimal used = request.getUsedGram();
        BigDecimal waste = request.getWasteGram();
        BigDecimal returned = request.getReturnedGram();

        // Enforce Formula: issuedGram = usedGram + wasteGram + returnedGram
        BigDecimal sumAccounting = used.add(waste).add(returned);
        if (issued.compareTo(sumAccounting) != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_RECONCILIATION_FORMULA",
                    "Formula violation: issued (" + issued + "g) != used (" + used +
                            "g) + waste (" + waste + "g) + returned (" + returned + "g). Sum is " + sumAccounting + "g");
        }

        consumption.setUsedGram(used);
        consumption.setWasteGram(waste);
        consumption.setReturnedGram(returned);
        consumption.setReconciled(true);
        consumption.setReconciledAt(Instant.now());
        consumption.setReconciledBy(actorId);
        consumption.setNote(request.getNote());

        // Restock returned material back into warehouse inventory balance!
        if (returned.compareTo(BigDecimal.ZERO) > 0) {
            InventoryBalance balance = balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(
                    consumption.getWarehouse().getId(), consumption.getMaterial().getId())
                    .orElseGet(() -> balanceRepository.save(InventoryBalance.builder()
                            .warehouse(consumption.getWarehouse())
                            .material(consumption.getMaterial())
                            .onHandGram(BigDecimal.ZERO)
                            .reservedGram(BigDecimal.ZERO)
                            .build()));

            BigDecimal beforeOnHand = balance.getOnHandGram();
            BigDecimal afterOnHand = beforeOnHand.add(returned);
            BigDecimal beforeReserved = balance.getReservedGram();

            balance.setOnHandGram(afterOnHand);
            balanceRepository.save(balance);

            // Immutable transaction for Restock Return
            InventoryTransaction tx = InventoryTransaction.builder()
                    .transactionType(InventoryTransactionType.PRINT_RETURN)
                    .warehouse(consumption.getWarehouse())
                    .material(consumption.getMaterial())
                    .deltaOnHandGram(returned)
                    .deltaReservedGram(BigDecimal.ZERO)
                    .beforeOnHandGram(beforeOnHand)
                    .afterOnHandGram(afterOnHand)
                    .beforeReservedGram(beforeReserved)
                    .afterReservedGram(beforeReserved)
                    .printJobId(job.getId())
                    .referenceId(job.getJobNumber())
                    .performedBy(actorId)
                    .build();
            transactionRepository.save(tx);
        }

        PrintJobConsumption saved = consumptionRepository.save(consumption);

        // Update Job Status
        if (job.getStatus() == PrintJobStatus.PRINTING || job.getStatus() == PrintJobStatus.ISSUED) {
            job.setStatus(PrintJobStatus.COMPLETED);
            job.setEndedAt(Instant.now());
            if (job.getPrinter() != null) {
                job.getPrinter().setStatus(PrinterStatus.IDLE);
                printerRepository.save(job.getPrinter());
            }
            PrintJob savedJob = printJobRepository.save(job);
            publishPrintJobEvent(savedJob != null ? savedJob : job, "PRINT_JOB_COMPLETED", java.util.Map.of("progress", 100));
        }

        log.info("Consumption reconciled for Job {}: Used={}g, Waste={}g, Returned={}g",
                job.getJobNumber(), used, waste, returned);

        return PrintJobConsumptionResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PrintJobConsumptionResponse getConsumption(UUID printJobId) {
        PrintJobConsumption consumption = consumptionRepository.findByPrintJobId(printJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumption record not found for job id: " + printJobId));
        return PrintJobConsumptionResponse.fromEntity(consumption);
    }

    private void publishPrintJobEvent(PrintJob job, String eventType, java.util.Map<String, Object> extra) {
        if (job == null || job.getId() == null) {
            return;
        }
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("jobId", job.getId());
            payload.put("jobNumber", job.getJobNumber());
            payload.put("orderId", job.getOrderId() != null ? job.getOrderId() : "");
            payload.put("status", job.getStatus().name());
            payload.put("isOutsource", job.isOutsource());
            if (job.getPrinter() != null) {
                payload.put("printerId", job.getPrinter().getId());
                payload.put("printerName", job.getPrinter().getName());
            }
            if (extra != null) {
                payload.putAll(extra);
            }
            outboxPublisher.publish("PRINT_JOB", job.getId().toString(), eventType, payload);
        } catch (Exception e) {
            log.warn("Failed to publish outbox event for print job {}: {}", job.getId(), e.getMessage());
        }
    }
}

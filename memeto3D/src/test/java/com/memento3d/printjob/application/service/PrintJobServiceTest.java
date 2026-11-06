package com.memento3d.printjob.application.service;

import com.memento3d.inventory.domain.model.InventoryBalance;
import com.memento3d.inventory.domain.model.StockReservation;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryBalanceRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.memento3d.inventory.infrastructure.persistence.repository.StockReservationRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printjob.api.request.ConsumptionReconciliationRequest;
import com.memento3d.printjob.api.request.MaterialIssueRequest;
import com.memento3d.printjob.api.request.ReserveStockRequest;
import com.memento3d.printjob.api.response.PrintJobConsumptionResponse;
import com.memento3d.printjob.api.response.StockReservationResponse;
import com.memento3d.printjob.application.service.impl.PrintJobServiceImpl;
import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobConsumption;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobConsumptionRepository;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.shared.exception.BusinessException;
import com.memento3d.warehouse.domain.model.Warehouse;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintJobServiceTest {
    @Mock private com.memento3d.customization.application.service.PrintDesignService printDesignService;

    @Mock private PrintJobRepository printJobRepository;
    @Mock private StockReservationRepository reservationRepository;
    @Mock private PrintJobConsumptionRepository consumptionRepository;
    @Mock private InventoryBalanceRepository balanceRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private PrinterRepository printerRepository;
    @Mock private com.memento3d.orders.infrastructure.persistence.repository.OrderRepository orderRepository;
    @Mock private com.memento3d.modelreview.infrastructure.persistence.repository.ModelVersionRepository modelVersionRepository;
    @Mock private com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;

    @InjectMocks
    private PrintJobServiceImpl printJobService;

    private UUID jobId;
    private UUID warehouseId;
    private UUID materialId;
    private PrintJob mockJob;
    private Warehouse mockWarehouse;
    private Material mockMaterial;
    private InventoryBalance mockBalance;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        materialId = UUID.randomUUID();

        mockMaterial = Material.builder()
                .id(materialId)
                .sku("PLA-WHT-001")
                .name("eSun PLA+ Cold White")
                .build();

        mockWarehouse = Warehouse.builder()
                .id(warehouseId)
                .code("WH-HCM-01")
                .name("Thu Duc Central")
                .build();

        mockJob = PrintJob.builder()
                .id(jobId)
                .jobNumber("JOB-TEST-001")
                .material(mockMaterial)
                .estimatedGram(new BigDecimal("400.00"))
                .status(PrintJobStatus.CREATED)
                .build();

        mockBalance = InventoryBalance.builder()
                .id(UUID.randomUUID())
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .onHandGram(new BigDecimal("1000.00"))
                .reservedGram(BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("reserveStock: reserves available quantity and transitions status to RESERVED (F06)")
    void reserveStock_success() {
        ReserveStockRequest request = ReserveStockRequest.builder()
                .warehouseId(warehouseId)
                .reservedGram(new BigDecimal("400.00"))
                .build();

        when(printJobRepository.findById(jobId)).thenReturn(Optional.of(mockJob));
        when(reservationRepository.existsByPrintJobIdAndStatus(jobId, "ACTIVE")).thenReturn(false);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(mockWarehouse));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(mockBalance));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        StockReservationResponse response = printJobService.reserveStock(jobId, request, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(mockBalance.getReservedGram()).isEqualTo(new BigDecimal("400.00"));
        assertThat(mockJob.getStatus()).isEqualTo(PrintJobStatus.RESERVED);
        verify(balanceRepository).save(mockBalance);
        verify(transactionRepository).save(any());
    }

    @Test
    @DisplayName("reserveStock: rejects duplicate reservation on the same job (F06)")
    void reserveStock_duplicateReservation() {
        ReserveStockRequest request = ReserveStockRequest.builder()
                .warehouseId(warehouseId)
                .reservedGram(new BigDecimal("400.00"))
                .build();

        when(printJobRepository.findById(jobId)).thenReturn(Optional.of(mockJob));
        when(reservationRepository.existsByPrintJobIdAndStatus(jobId, "ACTIVE")).thenReturn(true);

        assertThatThrownBy(() -> printJobService.reserveStock(jobId, request, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getErrorCode()).isEqualTo("DUPLICATE_RESERVATION");
                });

        verify(balanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("reconcileConsumption: strictly enforces formula issued = used + waste + returned (F07)")
    void reconcileConsumption_enforcesFormula() {
        PrintJobConsumption consumption = PrintJobConsumption.builder()
                .id(UUID.randomUUID())
                .printJob(mockJob)
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .issuedGram(new BigDecimal("400.00"))
                .build();

        ConsumptionReconciliationRequest invalidRequest = ConsumptionReconciliationRequest.builder()
                .usedGram(new BigDecimal("350.00"))
                .wasteGram(new BigDecimal("30.00"))
                .returnedGram(new BigDecimal("10.00")) // Sum is 390 != 400!
                .build();

        when(printJobRepository.findById(jobId)).thenReturn(Optional.of(mockJob));
        when(consumptionRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(consumption));

        assertThatThrownBy(() -> printJobService.reconcileConsumption(jobId, invalidRequest, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorCode()).isEqualTo("INVALID_RECONCILIATION_FORMULA");
                });
    }

    @Test
    @DisplayName("reconcileConsumption: restocks returned quantity and completes job (E2E Scenario)")
    void reconcileConsumption_successWithRestock() {
        PrintJobConsumption consumption = PrintJobConsumption.builder()
                .id(UUID.randomUUID())
                .printJob(mockJob)
                .warehouse(mockWarehouse)
                .material(mockMaterial)
                .issuedGram(new BigDecimal("400.00"))
                .build();

        ConsumptionReconciliationRequest validRequest = ConsumptionReconciliationRequest.builder()
                .usedGram(new BigDecimal("350.00"))
                .wasteGram(new BigDecimal("30.00"))
                .returnedGram(new BigDecimal("20.00")) // 350 + 30 + 20 = 400!
                .build();

        // Job is in PRINTING state before reconciling
        mockJob.setStatus(PrintJobStatus.PRINTING);
        // Suppose balance after issuing 400g has onHand = 1600g
        mockBalance.setOnHandGram(new BigDecimal("1600.00"));

        when(printJobRepository.findById(jobId)).thenReturn(Optional.of(mockJob));
        when(consumptionRepository.findByPrintJobId(jobId)).thenReturn(Optional.of(consumption));
        when(balanceRepository.findByWarehouseIdAndMaterialIdForUpdate(warehouseId, materialId))
                .thenReturn(Optional.of(mockBalance));
        when(consumptionRepository.save(any(PrintJobConsumption.class))).thenAnswer(inv -> inv.getArgument(0));

        PrintJobConsumptionResponse response = printJobService.reconcileConsumption(jobId, validRequest, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.isReconciled()).isTrue();
        // Returned 20g added to 1600g = 1620g!
        assertThat(mockBalance.getOnHandGram()).isEqualTo(new BigDecimal("1620.00"));
        assertThat(mockJob.getStatus()).isEqualTo(PrintJobStatus.COMPLETED);
        verify(transactionRepository).save(any());
    }
}

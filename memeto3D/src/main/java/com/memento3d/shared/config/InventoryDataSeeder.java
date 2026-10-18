package com.memento3d.shared.config;

import com.memento3d.inventory.api.request.CreateStockReceiptRequest;
import com.memento3d.inventory.application.service.InventoryService;
import com.memento3d.material.api.request.CreateMaterialRequest;
import com.memento3d.material.api.response.MaterialResponse;
import com.memento3d.material.application.service.MaterialService;
import com.memento3d.material.domain.model.MaterialType;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.printjob.api.request.ConsumptionReconciliationRequest;
import com.memento3d.printjob.api.request.CreatePrintJobRequest;
import com.memento3d.printjob.api.request.MaterialIssueRequest;
import com.memento3d.printjob.api.request.ReserveStockRequest;
import com.memento3d.printjob.api.response.PrintJobResponse;
import com.memento3d.printjob.application.service.PrintJobService;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.application.service.WarehouseService;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryDataSeeder implements CommandLineRunner {

    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final PrintJobService printJobService;

    @Override
    public void run(String... args) {
        if (materialRepository.count() > 0) {
            log.info("Inventory data already seeded. Skipping initial seeding.");
            return;
        }

        log.info("Starting initial seed for Materials, Warehouses, Inventory Balances and Print Jobs...");

        // 1. Seed Materials
        MaterialResponse plaWhite = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("PLA-WHT-001")
                .name("eSun PLA+ Cold White")
                .materialType(MaterialType.PLA)
                .color("Cold White")
                .colorHex("#F8FAFC")
                .density(new BigDecimal("1.24"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(205)
                .printTempMax(225)
                .bedTempMin(55)
                .bedTempMax(65)
                .unitCost(new BigDecimal("250.00"))
                .description("High-rigidity PLA+ for detailed engineering miniatures")
                .build(), null);

        MaterialResponse plaBlack = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("PLA-BLK-002")
                .name("Polymaker PolyLite PLA Galaxy Black")
                .materialType(MaterialType.PLA)
                .color("Galaxy Black")
                .colorHex("#0F172A")
                .density(new BigDecimal("1.24"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(200)
                .printTempMax(220)
                .bedTempMin(50)
                .bedTempMax(60)
                .unitCost(new BigDecimal("280.00"))
                .description("Matte black surface finish with sparkle flakes")
                .build(), null);

        MaterialResponse petgOrange = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("PETG-ORG-003")
                .name("Prusament PETG Prusa Orange")
                .materialType(MaterialType.PETG)
                .color("Prusa Orange")
                .colorHex("#F97316")
                .density(new BigDecimal("1.27"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(230)
                .printTempMax(250)
                .bedTempMin(75)
                .bedTempMax(85)
                .unitCost(new BigDecimal("350.00"))
                .description("Impact and heat resistant mechanical prototype material")
                .build(), null);

        MaterialResponse absGrey = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("ABS-GRY-004")
                .name("Sunlu ABS Industrial Grey")
                .materialType(MaterialType.ABS)
                .color("Industrial Grey")
                .colorHex("#64748B")
                .density(new BigDecimal("1.04"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(240)
                .printTempMax(260)
                .bedTempMin(95)
                .bedTempMax(110)
                .unitCost(new BigDecimal("290.00"))
                .description("High-temperature functional parts for enclosed printers")
                .build(), null);

        MaterialResponse resinClear = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("RESIN-CLR-005")
                .name("Anycubic Standard UV Resin Clear")
                .materialType(MaterialType.RESIN)
                .color("Crystal Clear")
                .colorHex("#E2E8F0")
                .density(new BigDecimal("1.15"))
                .unitCost(new BigDecimal("750.00"))
                .description("405nm photopolymer resin for ultra-high resolution DLP printing")
                .build(), null);

        // 2. Seed Warehouses
        WarehouseResponse whHcm = warehouseService.createWarehouse(CreateWarehouseRequest.builder()
                .code("WH-HCM-01")
                .name("Central Hub Thu Duc")
                .location("Lot T2-4, D1 Street, Saigon High-Tech Park, Thu Duc City, HCMC")
                .build(), null);

        WarehouseResponse whHn = warehouseService.createWarehouse(CreateWarehouseRequest.builder()
                .code("WH-HN-02")
                .name("North FabLab Cau Giay")
                .location("No. 18 Duy Tan Street, Dich Vong Hau, Cau Giay, Hanoi")
                .build(), null);

        WarehouseResponse whDn = warehouseService.createWarehouse(CreateWarehouseRequest.builder()
                .code("WH-DN-03")
                .name("Central Hub Da Nang")
                .location("Road 3, Hoa Khanh Industrial Zone, Lien Chieu, Da Nang")
                .build(), null);

        // 3. Seed Stock In (Goods Receipts)
        inventoryService.createReceipt(CreateStockReceiptRequest.builder()
                .warehouseId(whHcm.getId())
                .materialId(plaWhite.getId())
                .quantityGram(new BigDecimal("5000.00"))
                .unitCost(new BigDecimal("250.00"))
                .supplier("eSun Vietnam Distributor")
                .batchNumber("LOT-202610-01")
                .idempotencyKey("SEED-REC-001")
                .note("Initial stock import 5kg cold white")
                .build(), null);

        inventoryService.createReceipt(CreateStockReceiptRequest.builder()
                .warehouseId(whHcm.getId())
                .materialId(petgOrange.getId())
                .quantityGram(new BigDecimal("3000.00"))
                .unitCost(new BigDecimal("350.00"))
                .supplier("Prusa Research Direct")
                .batchNumber("LOT-202610-02")
                .idempotencyKey("SEED-REC-002")
                .note("Initial stock import 3kg PETG orange")
                .build(), null);

        inventoryService.createReceipt(CreateStockReceiptRequest.builder()
                .warehouseId(whHn.getId())
                .materialId(plaBlack.getId())
                .quantityGram(new BigDecimal("4000.00"))
                .unitCost(new BigDecimal("280.00"))
                .supplier("Polymaker Global")
                .batchNumber("LOT-202610-03")
                .idempotencyKey("SEED-REC-003")
                .note("Initial stock import Hanoi FabLab")
                .build(), null);

        // 4. Seed E2E Print Job Flow (F06 + F07)
        PrintJobResponse job1 = printJobService.createPrintJob(CreatePrintJobRequest.builder()
                .materialId(plaWhite.getId())
                .estimatedGram(new BigDecimal("400.00"))
                .estimatedDurationSeconds(14400)
                .build(), null);

        // Reserve 400g
        printJobService.reserveStock(job1.getId(), ReserveStockRequest.builder()
                .warehouseId(whHcm.getId())
                .reservedGram(new BigDecimal("400.00"))
                .build(), null);

        // Issue 400g to printer
        printJobService.issueMaterial(job1.getId(), MaterialIssueRequest.builder()
                .warehouseId(whHcm.getId())
                .issuedGram(new BigDecimal("400.00"))
                .note("Issued to Prusa MK4 #1")
                .build(), null);

        // Reconcile: used 350g, waste 30g, returned 20g (Restocks 20g back into WH-HCM-01!)
        printJobService.reconcileConsumption(job1.getId(), ConsumptionReconciliationRequest.builder()
                .usedGram(new BigDecimal("350.00"))
                .wasteGram(new BigDecimal("30.00"))
                .returnedGram(new BigDecimal("20.00"))
                .note("QC Passed - Dimensional accuracy within 0.05mm. 20g spool tail returned.")
                .build(), null);

        // Seed second pending Print Job with active reservation
        PrintJobResponse job2 = printJobService.createPrintJob(CreatePrintJobRequest.builder()
                .materialId(petgOrange.getId())
                .estimatedGram(new BigDecimal("550.00"))
                .estimatedDurationSeconds(18000)
                .build(), null);

        printJobService.reserveStock(job2.getId(), ReserveStockRequest.builder()
                .warehouseId(whHcm.getId())
                .reservedGram(new BigDecimal("550.00"))
                .build(), null);

        log.info("Inventory and Print Job seed data initialized successfully!");
    }
}

# MEMENTO3D — 3D PRINTING & JOB EXECUTION FLOW

## 1. Overview
Khối **3D Printing Management** điều phối quá trình sản xuất thực tế tại xưởng in 3D: từ việc tiếp nhận file G-code từ đơn hàng, lập lịch lệnh in (`PrintJob`), gán máy in phù hợp, theo dõi thời gian thực, xử lý sự cố đứt sợi/mất điện, cho tới khi hoàn thành và tự động trừ kho vật liệu tiêu hao.

---

## 2. Print Job State Machine

```mermaid
stateDiagram-v2
    [*] --> DRAFT : Operator prepares job
    DRAFT --> ASSIGNED : Printer allocated
    ASSIGNED --> PREHEATING : Bed & Nozzle heating up
    PREHEATING --> PRINTING : Extrusion starts
    
    PRINTING --> PAUSED : Filament runout / Manual pause
    PAUSED --> PRINTING : Resume print
    
    PRINTING --> CANCELLED : Bed adhesion fail / Spaghetti detected
    PRINTING --> FAILED : Thermal runaway / Extruder stall
    
    PRINTING --> COMPLETED : Print finished at 100%
    
    COMPLETED --> POST_PROCESSING : Cleaning, curing, support removal
    POST_PROCESSING --> QC_PASSED : Dimension & surface verified
    QC_PASSED --> [*]
```

---

## 3. End-to-End Printing Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator / Manager
    participant API as PrintJobController
    participant SVC as PrintJobServiceImpl
    participant PRT as PrinterController / Agent
    participant OCTO as OctoPrint Server / 3D Printer
    participant WMS as WarehouseService
    participant DB as PostgreSQL

    Op->>API: POST /api/v1/print-jobs {orderId, gcodeUrl, estimatedGrams}
    API->>SVC: createPrintJob(request)
    SVC->>DB: INSERT INTO print_jobs (status=DRAFT)
    
    Op->>API: POST /api/v1/print-jobs/{id}/assign {printerId}
    API->>SVC: assignPrinter(jobId, printerId)
    SVC->>DB: SELECT * FROM printers WHERE id = printerId
    alt Printer is busy or offline
        SVC-->>Op: 409 Conflict (PRINTER_BUSY)
    else Printer IDLE
        SVC->>DB: UPDATE printers SET status = 'BUSY'
        SVC->>DB: UPDATE print_jobs SET status = 'ASSIGNED', printer_id = printerId
    end

    Op->>API: POST /api/v1/print-jobs/{id}/start
    API->>SVC: startJob(jobId)
    SVC->>OCTO: Send Start Job Command via Agent Gateway
    OCTO-->>SVC: Acknowledge (Job Started)
    SVC->>DB: UPDATE print_jobs SET status = 'PRINTING', started_at = NOW()

    loop Every 10-15 seconds (Telemetry Stream)
        OCTO->>PRT: POST /api/v1/printers/agent/job-progress {jobId, progressPercent, currentLayer, nozzleTemp}
        PRT->>DB: UPDATE print_jobs SET progress_percentage = ?
        PRT->>DB: UPDATE printers SET nozzle_temp = ?, bed_temp = ?
    end

    OCTO->>PRT: POST /api/v1/printers/agent/job-progress {progress=100%, status=COMPLETED}
    PRT->>SVC: onJobCompleted(jobId)
    SVC->>DB: UPDATE print_jobs SET status = 'COMPLETED', finished_at = NOW()
    SVC->>DB: UPDATE printers SET status = 'IDLE'
    
    Note over SVC,WMS: Auto Deduct Filament from Warehouse
    SVC->>WMS: recordPrintConsumption(jobId, actualGramsUsed)
    WMS->>DB: UPDATE inventory_balances SET quantity = quantity - ?
    WMS->>DB: INSERT INTO stock_ledger (type='PRINT_CONSUMPTION', change=-actualGrams)
    
    SVC-->>Op: Print Job Successfully Finished & Inventory Updated
```

---

## 4. Error Handling & Recovery Policies

1. **Cảm biến hết sợi (Filament Runout)**:
   - Khi cảm biến phát hiện hết nhựa, OctoPrint tự động gửi mã trạng thái `PAUSED`.
   - Hệ thống Memento3D gửi notification cho Operator đến nạp cuộn nhựa mới và bấm "Resume".
2. **Lỗi kẹt nhiệt (Thermal Runaway / Extruder Jam)**:
   - Nếu nhiệt độ đầu đùn lệch quá $\pm 5^\circ C$ so với mục tiêu trong hơn 30 giây, firmware máy in sẽ tự động ngắt điện thanh nhiệt để phòng chống cháy nổ.
   - Trạng thái lệnh in chuyển sang `FAILED`, máy in chuyển sang `MAINTENANCE`.
3. **Thất bại bám dính bàn in (Layer Shift / Spaghetti Defect)**:
   - Kỹ thuật viên quan sát qua camera hoặc AI Obico phát hiện mẫu bị bong bàn in.
   - Bấm lệnh `CANCEL`. Vật liệu bị hỏng được ghi nhận vào kho dưới dạng hao hụt phế phẩm (`SCRAP_ISSUE`).

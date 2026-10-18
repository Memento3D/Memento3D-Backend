# MEMENTO3D — 3D RECONSTRUCTION & AI WORKER PIPELINE

## 1. Overview
Khối **3D Reconstruction** là trái tim công nghệ của Memento3D, có nhiệm vụ chuyển đổi tập hợp các ảnh chụp 2D đa góc độ của người hoặc vật thể thành mô hình lưới đa giác 3D (3D Polygonal Mesh) kèm vân bề mặt (Texture Map) chân thực với độ chính xác cao.

---

## 2. Photogrammetry & AI Reconstruction Flow

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Customer
    participant FE as Web/Mobile Client
    participant SCAN as ScanController
    participant QC as ScanQcController
    participant DISP as ReconstructionDispatcher
    participant WORKER as AI/GPU Reconstruction Worker
    participant CLOUD as Cloud Storage (Cloudinary/S3)
    participant DB as PostgreSQL

    Customer->>FE: Select 20-100 photos around subject
    FE->>SCAN: POST /api/v1/scans/upload (multipart)
    SCAN->>CLOUD: Stream images to Cloud Storage
    SCAN->>DB: INSERT INTO scans (status=PENDING_REVIEW), scan_images
    SCAN-->>FE: Return ScanResponse (UUID)

    Note over QC,DB: Manual Quality Gate (Operator checks photo quality)
    QC->>DB: Fetch scans WHERE status = 'PENDING_REVIEW'
    alt Photos are blurry or insufficient
        QC->>DB: UPDATE scans SET status = 'REJECTED', qc_feedback = 'Blurry images'
        QC-->>Customer: Notify customer to re-take photos
    else Photos meet quality criteria
        QC->>DB: UPDATE scans SET status = 'APPROVED'
        QC->>DISP: dispatchJob(scanId)
    end

    DISP->>DB: UPDATE scans SET status = 'QUEUED'
    DISP->>WORKER: Dispatch task payload (Image URLs, parameters)
    
    WORKER->>DB: UPDATE scans SET status = 'PROCESSING', progress = 10%
    Note over WORKER: 1. Feature Detection (SIFT)<br/>2. Structure from Motion (SfM)<br/>3. Multi-View Stereo (MVS)<br/>4. Poisson Surface Reconstruction<br/>5. Texture Mapping & Decimation
    
    WORKER->>CLOUD: Upload model files (.glb, .obj, .stl, thumbnail.jpg)
    WORKER->>SCAN: POST /api/v1/scans/{id}/callback {status=COMPLETED, artifacts}
    SCAN->>DB: INSERT INTO model_outputs (glb_url, obj_url, stl_url, poly_count, size_mb)
    SCAN->>DB: UPDATE scans SET status = 'COMPLETED', completed_at = NOW()
    
    Customer->>FE: Open 3D Viewer (Three.js / OrbitControls)
    FE->>SCAN: GET /api/v1/models/scan/{scanId}
    SCAN-->>FE: Return ModelOutputResponse with GLB URL
    FE->>FE: Render interactive 3D model on canvas
```

---

## 3. Reconstruction State Machine

```mermaid
stateDiagram-v2
    [*] --> UPLOADING : Client initiates upload
    UPLOADING --> PENDING_REVIEW : Upload completed
    
    PENDING_REVIEW --> REJECTED : Operator rejects (bad lighting/blurry)
    PENDING_REVIEW --> APPROVED : Operator approves quality
    
    APPROVED --> QUEUED : Dispatched to queue
    QUEUED --> PROCESSING : GPU Worker picks up job
    
    PROCESSING --> PROCESSING : Progress updates (25%, 50%, 75%)
    PROCESSING --> FAILED : Out of memory / Feature match fail
    FAILED --> QUEUED : Auto-retry (max 3 times)
    
    PROCESSING --> COMPLETED : Mesh & textures generated
    COMPLETED --> [*]
    REJECTED --> [*]
```

---

## 4. Current Implementation Status vs Architecture

### 4.1 Implementation thực tế trong source code
- **Mock Dispatcher (`ReconstructionDispatcherMock.java`)**:
  - Khi môi trường dev hoặc chưa kết nối cụm GPU thật, dispatcher chạy một background executor tự động sinh tiến độ giả lập từ `10% -> 50% -> 100%` trong 15-30 giây.
  - Tự động gắn link mẫu 3D demo chuẩn (`.glb`) vào bảng `model_outputs`.
- **Production Dispatcher (`ReconstructionDispatcherImpl.java`)**:
  - Sử dụng REST API Webhook hoặc Message Queue (RabbitMQ / Redis PubSub) để đẩy job sang GPU Worker bên ngoài chạy Python (sử dụng các thư viện như COLMAP, OpenMVS, hoặc NeRF/Gaussian Splatting to Mesh).

---

## 5. Artifacts Output Structure

Mỗi phiên dựng thành công sẽ tạo ra các file chuẩn:
1. `model.glb`: Định dạng chuẩn tối ưu cho hiển thị Web 3D (Three.js / `<model-viewer>`), nhúng sẵn texture và animation (nếu có).
2. `model.obj` + `model.mtl`: Định dạng chuẩn cho các phần mềm dựng hình 3D (Blender, Maya, 3ds Max).
3. `model.stl`: Định dạng chuẩn không chứa màu sắc, tối ưu riêng cho việc nạp vào phần mềm Slicer in 3D.
4. `thumbnail.webp`: Ảnh chụp góc phối cảnh 3/4 của mô hình để hiển thị danh sách trong Dashboard.

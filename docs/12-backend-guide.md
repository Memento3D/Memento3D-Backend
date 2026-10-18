# MEMENTO3D — BACKEND ARCHITECTURE & DEVELOPER GUIDE

## 1. Overview & Technology Stack
Phần mềm Backend của Memento3D (`Memento3D-Backend/memeto3D`) được xây dựng theo kiến trúc **Modular Monolith** kết hợp nguyên lý thiết kế **Package-by-Feature / Domain-Driven**:

- **Ngôn ngữ**: Java 21 (LTS)
- **Framework**: Spring Boot 3.4.x / 4.x
- **ORM / Persistence**: Spring Data JPA, Hibernate 6.x
- **Database**: PostgreSQL 16 (Hỗ trợ Neon Serverless Postgres với kết nối SSL)
- **Cache & Token Store**: Redis 7.x
- **Data Mapping**: MapStruct 1.5+ (Sinh mã chuyển đổi DTO ↔ Entity tại compile-time không dùng reflection)
- **Code Generation**: Project Lombok
- **API Documentation**: Springdoc OpenAPI 3.x (Swagger UI)
- **Database Migration**: Flyway / Liquibase

---

## 2. Package Architecture (Package-by-Feature)

```text
com.memento3d/
├── auth/                       # Module Xác thực & JWT
│   ├── api/                    # AuthController, Request/Response DTOs
│   ├── application/            # AuthenticationService, JwtService, OAuth2Service
│   ├── domain/                 # UserEntity, Role, UserStatus
│   └── infrastructure/         # UserRepository, JwtTokenFilter, SecurityConfig
├── scans/                      # Module Photogrammetry & 3D Reconstruction
│   ├── api/                    # ScanController, ScanQcController
│   ├── application/            # ScanService, ScanQcService, ScanAccessGuard
│   ├── domain/                 # ScanEntity, ScanImageEntity, ScanStatus
│   └── infrastructure/         # ScanRepository, CloudinaryStorageClient
├── model/                      # Module Mô hình 3D (3D Model Outputs)
│   ├── api/                    # ModelController
│   ├── application/            # ModelService
│   ├── domain/                 # ModelOutputEntity, ModelFormat
│   └── infrastructure/         # ModelOutputRepository
├── order/                      # Module Đơn hàng in 3D
│   ├── api/                    # OrderController
│   ├── application/            # OrderService, OrderPricingService
│   ├── domain/                 # OrderEntity, OrderItemEntity, OrderStatus
│   └── infrastructure/         # OrderRepository
├── payment/                    # Module Cổng thanh toán (MoMo, ZaloPay)
│   ├── api/                    # PaymentController
│   ├── application/            # PaymentService, MomoGateway, ZaloPayGateway
│   ├── domain/                 # PaymentTransactionEntity, PaymentStatus
│   └── infrastructure/         # PaymentRepository
├── printer/                    # Module Máy in 3D & IoT Agent Gateway
│   ├── api/                    # PrinterController, PrinterAgentController, PrintJobController
│   ├── application/            # PrinterService, PrintJobService, TelemetryService
│   ├── domain/                 # PrinterEntity, PrintJobEntity, PrinterStatus
│   └── infrastructure/         # PrinterRepository, PrintJobRepository
├── warehouse/                  # Module Quản lý Kho vật liệu (WMS)
│   ├── api/                    # WarehouseController, MaterialController
│   ├── application/            # WarehouseService, MaterialService, InventoryAccountingService
│   ├── domain/                 # WarehouseEntity, MaterialEntity, InventoryBalanceEntity, StockLedgerEntity
│   └── infrastructure/         # WarehouseRepository, StockLedgerRepository
└── common/                     # Thư viện tiện ích dùng chung
    ├── exception/              # GlobalExceptionHandler, Custom Exceptions
    ├── response/               # ApiResponse, PageResponse
    └── config/                 # RedisConfig, OpenApiConfig, CorsConfig
```

---

## 3. Layered Processing Lifecycle

```text
HTTP Request
     ↓
CorsFilter / SecurityFilterChain (JwtAuthenticationFilter)
     ↓
Controller (@RestController, @RequestMapping)
     ↓
DTO Validation (Jakarta Validation: @NotBlank, @Min, @Size)
     ↓
Service Layer (@Service, @Transactional)
     ↓
Repository Layer (Spring Data JPA JpaRepository)
     ↓
Database (PostgreSQL / Neon)
     ↓
Entity to DTO Mapping (MapStruct @Mapper)
     ↓
ApiResponse<DTO>
     ↓
HTTP 200 OK / HTTP 201 Created
```

### 3.1 Quy tắc Phân định Trách nhiệm (Separation of Concerns)
1. **Controller**:
   - Tuyệt đối không chứa logic nghiệp vụ, tính toán tiền nong, hoặc câu lệnh truy vấn SQL.
   - Chỉ chịu trách nhiệm tiếp nhận HTTP Request, gọi DTO Validation, ủy quyền cho Service và bọc kết quả vào `ApiResponse<T>`.
2. **Service**:
   - Chứa 100% logic nghiệp vụ và ranh giới Transaction (`@Transactional`).
   - Kiểm tra phân quyền nghiệp vụ (Ownership guard), xác thực trạng thái máy in, kiểm tra tồn kho.
3. **Repository**:
   - Chỉ chứa các câu lệnh truy vấn dữ liệu (Spring Data Method Queries, JPQL `@Query`, hoặc Native Query khi cần tối ưu performance).
4. **Entity**:
   - Không bao giờ trả trực tiếp Entity ra ngoài REST Controller để phòng tránh lộ cấu trúc cơ sở dữ liệu và lỗi `LazyInitializationException` (N+1 query).

---

## 4. Scheduled Jobs Catalog (`@Scheduled`)

Hệ thống có 6 tiến trình chạy ngầm định kỳ:

| Tên Tiến Trình (Scheduler) | Tần suất thực thi | Nhiệm vụ chính |
|---|---|---|
| `PrinterHeartbeatScheduler` | `*/15 * * * * *` (Mỗi 15 giây) | Quét các máy in quá 60s không gửi telemetry để chuyển sang `OFFLINE`. |
| `ScanQcTimeoutScheduler` | `0 */10 * * * *` (Mỗi 10 phút) | Cảnh báo các bản scan chờ QC quá 24h chưa được Operator xử lý. |
| `PaymentTimeoutScheduler` | `0 */5 * * * *` (Mỗi 5 phút) | Tự động hủy các giao dịch thanh toán quá hạn (Sau 30 phút chưa thanh toán). |
| `TokenBlacklistCleaner` | `0 0 * * * *` (Mỗi 1 giờ) | Dọn dẹp các token đã quá hạn khỏi Redis Blacklist. |
| `InventoryAlertScheduler` | `0 0 8 * * *` (8:00 AM hàng ngày) | Gửi email thông báo các loại vật liệu có tồn kho dưới ngưỡng an toàn (`min_threshold`). |
| `PrintJobProgressScheduler`| `*/10 * * * * *` (Mỗi 10 giây) | Đồng bộ tiến độ in ấn từ OctoPrint vào cơ sở dữ liệu trung tâm. |

---

## 5. Global Exception Handling

Tất cả các ngoại lệ trong hệ thống đều được bắt tập trung tại `GlobalExceptionHandler` (`@RestControllerAdvice`):

```json
{
  "success": false,
  "message": "Không tìm thấy tài nguyên yêu cầu",
  "errors": [
    "Material with ID e3b0c442-... does not exist"
  ],
  "timestamp": "2026-10-18T18:00:00Z"
}
```

Các Exception phổ biến:
- `ResourceNotFoundException` -> HTTP 404
- `IllegalStateException` / `InvalidStateException` -> HTTP 400
- `AccessDeniedException` -> HTTP 403
- `BadCredentialsException` -> HTTP 401
- `DuplicateKeyException` -> HTTP 409
- `InsufficientStockException` -> HTTP 400

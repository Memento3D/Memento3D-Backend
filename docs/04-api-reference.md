# MEMENTO3D — API REFERENCE SPECIFICATION

## 1. Overview & Conventions
- **Base URL**: `http://localhost:8080/api/v1` (Production: `https://api.memento3d.vn/api/v1`)
- **Format**: `application/json` (trừ multipart upload)
- **Authentication**: `Authorization: Bearer <JWT_ACCESS_TOKEN>`
- **IoT Agent**: `X-Agent-Token: <SECRET_AGENT_TOKEN>`

### Standard API Response Envelope
Hầu hết các endpoint trả về envelope JSON:
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "errors": null,
  "timestamp": "2026-10-18T18:00:00Z"
}
```

---

## 2. Authentication API (`/api/v1/auth`)

| Method | Endpoint | Actor | Request Body | Response Body | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | `RegisterRequest` | `AuthResponse` | Đăng ký tài khoản Customer mới |
| `POST` | `/api/v1/auth/login` | Public | `LoginRequest` | `AuthResponse` | Đăng nhập bằng Email & Password |
| `POST` | `/api/v1/auth/refresh` | Public | `RefreshTokenRequest` | `AuthResponse` | Cấp lại Access Token mới |
| `POST` | `/api/v1/auth/oauth2/google` | Public | `OAuth2GoogleRequest` | `AuthResponse` | Đăng nhập / đăng ký bằng Google ID Token |
| `POST` | `/api/v1/auth/logout` | Authenticated | Header Bearer Token | `ApiResponse<Void>` | Đăng xuất & thu hồi token vào Redis |
| `GET` | `/api/v1/auth/me` | Authenticated | None | `UserResponse` | Lấy thông tin cá nhân hiện tại |

---

## 3. Scans & Upload API (`/api/v1/scans`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/scans/upload` | `CUSTOMER`, `ADMIN` | `multipart/form-data` (files, captureMode) | `ScanResponse` | Tải lên 20-200 ảnh chụp để tạo bản scan |
| `GET` | `/api/v1/scans/{id}` | Owner, `MANAGER`, `ADMIN` | Path `id` (UUID) | `ScanDetailResponse` | Chi tiết bản scan và danh sách ảnh gốc |
| `GET` | `/api/v1/scans/my` | `CUSTOMER` | Pageable (`page`, `size`) | `Page<ScanSummaryResponse>` | Danh sách scan của user hiện tại |
| `POST` | `/api/v1/scans/{id}/cancel`| Owner, `ADMIN` | Path `id` | `ScanResponse` | Hủy bản scan đang trong hàng đợi |

---

## 4. Scan Quality Control API (`/api/v1/scans/qc`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/scans/qc/queue` | `OPERATOR`, `MANAGER`, `ADMIN` | Filter (`status=PENDING_REVIEW`) | `List<ScanQcResponse>` | Danh sách các scan đang chờ duyệt QC |
| `POST` | `/api/v1/scans/qc/{id}/approve` | `OPERATOR`, `MANAGER`, `ADMIN` | Path `id`, `QcFeedbackRequest` | `ScanQcResponse` | Phê duyệt ảnh đủ chuẩn dựng 3D, đẩy sang AI |
| `POST` | `/api/v1/scans/qc/{id}/reject` | `OPERATOR`, `MANAGER`, `ADMIN` | Path `id`, `QcRejectRequest` (lý do mờ/thiếu sáng) | `ScanQcResponse` | Từ chối bản scan và gửi thông báo cho khách |

---

## 5. 3D Models API (`/api/v1/models`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/models/scan/{scanId}` | Owner, `MANAGER`, `ADMIN` | Path `scanId` | `ModelOutputResponse` | Lấy link tải GLB/OBJ/STL và preview 3D |
| `GET` | `/api/v1/models/{id}/download` | Owner, `ADMIN` | Path `id`, Param `format` | File Binary / Signed URL | Tải file mô hình 3D hoàn chỉnh |
| `POST` | `/api/v1/models/{id}/optimize` | `OPERATOR`, `ADMIN` | Path `id`, `OptimizeRequest` | `ModelOutputResponse` | Yêu cầu decimate / làm mịn mesh |

---

## 6. Orders API (`/api/v1/orders`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/orders` | `CUSTOMER`, `ADMIN` | `CreateOrderRequest` | `OrderResponse` | Tạo đơn đặt in mô hình 3D |
| `GET` | `/api/v1/orders` | Owner / `MANAGER`, `ADMIN` | Pageable, Status filter | `Page<OrderSummaryResponse>` | Lấy danh sách đơn hàng |
| `GET` | `/api/v1/orders/{id}` | Owner, `MANAGER`, `ADMIN`, `PROVIDER` | Path `id` | `OrderDetailResponse` | Xem chi tiết đơn hàng, lịch sử trạng thái |
| `PATCH` | `/api/v1/orders/{id}/cancel` | Owner, `ADMIN` | Path `id`, `CancelReasonRequest` | `OrderResponse` | Hủy đơn hàng (khi chưa PRINTING) |
| `PATCH` | `/api/v1/orders/{id}/status` | `MANAGER`, `ADMIN`, `OPERATOR` | Path `id`, `UpdateOrderStatusRequest` | `OrderResponse` | Chuyển trạng thái đơn hàng |

---

## 7. Payments API (`/api/v1/payments`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/payments/create` | `CUSTOMER`, `ADMIN` | `CreatePaymentRequest` (`orderId`, `method`) | `PaymentInitResponse` (`payUrl`, `qrCode`) | Khởi tạo giao dịch MoMo hoặc ZaloPay |
| `POST` | `/api/v1/payments/momo/ipn` | **Public / MoMo Webhook** | MoMo IPN Payload + HMAC Sig | `MomoIpnResponse` | Nhận kết quả thanh toán từ MoMo |
| `POST` | `/api/v1/payments/zalopay/callback` | **Public / ZaloPay Webhook** | ZaloPay Callback Payload + Mac | `ZaloPayCallbackResponse` | Nhận kết quả thanh toán từ ZaloPay |
| `GET` | `/api/v1/payments/{orderId}/status` | Owner, `ADMIN` | Path `orderId` | `PaymentStatusResponse` | Kiểm tra trạng thái giao dịch |

---

## 8. 3D Printers API (`/api/v1/printers`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/printers` | `OPERATOR`, `MANAGER`, `ADMIN` | Status filter | `List<PrinterResponse>` | Danh sách máy in và tình trạng kết nối |
| `POST` | `/api/v1/printers` | `MANAGER`, `ADMIN` | `CreatePrinterRequest` | `PrinterResponse` | Thêm máy in mới và sinh `agentToken` |
| `GET` | `/api/v1/printers/{id}` | `OPERATOR`, `MANAGER`, `ADMIN` | Path `id` | `PrinterDetailResponse` | Thông số kỹ thuật, nhiệt độ đầu đùn, bàn nhiệt |
| `PUT` | `/api/v1/printers/{id}` | `MANAGER`, `ADMIN` | `UpdatePrinterRequest` | `PrinterResponse` | Cập nhật cấu hình máy in |
| `DELETE`| `/api/v1/printers/{id}` | `ADMIN` | Path `id` | `ApiResponse<Void>` | Xóa hoặc vô hiệu hóa máy in |

---

## 9. OctoPrint IoT Agent API (`/api/v1/printers/agent`)

| Method | Endpoint | Actor | Header Required | Request Body | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/printers/agent/heartbeat` | IoT Agent | `X-Agent-Token` | `HeartbeatPayload` | Gửi ping định kỳ 15s xác nhận online |
| `POST` | `/api/v1/printers/agent/telemetry` | IoT Agent | `X-Agent-Token` | `TelemetryPayload` (temp, fan, pos) | Cập nhật nhiệt độ và trạng thái phần cứng |
| `POST` | `/api/v1/printers/agent/job-progress` | IoT Agent | `X-Agent-Token` | `JobProgressPayload` (layer, %, time) | Cập nhật tiến độ lệnh in thực tế |

---

## 10. Print Jobs API (`/api/v1/print-jobs`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/print-jobs` | `OPERATOR`, `MANAGER`, `ADMIN` | `CreatePrintJobRequest` | `PrintJobResponse` | Tạo lệnh in cho đơn hàng |
| `POST` | `/api/v1/print-jobs/{id}/assign` | `OPERATOR`, `MANAGER`, `ADMIN` | Path `id`, `AssignPrinterRequest` | `PrintJobResponse` | Gán máy in thích hợp cho lệnh in |
| `POST` | `/api/v1/print-jobs/{id}/start` | `OPERATOR`, `ADMIN` | Path `id` | `PrintJobResponse` | Bắn G-code sang OctoPrint và bắt đầu in |
| `POST` | `/api/v1/print-jobs/{id}/pause` | `OPERATOR`, `ADMIN` | Path `id` | `PrintJobResponse` | Tạm dừng in khi có sự cố |
| `POST` | `/api/v1/print-jobs/{id}/cancel` | `OPERATOR`, `ADMIN` | Path `id`, `CancelReasonRequest` | `PrintJobResponse` | Hủy lệnh in |
| `POST` | `/api/v1/print-jobs/{id}/complete` | `OPERATOR`, `ADMIN` | Path `id`, `JobCompleteRequest` | `PrintJobResponse` | Hoàn thành in, trừ kho vật liệu tiêu hao |

---

## 11. Warehouse Management API (`/api/v1/wms`)

| Method | Endpoint | Actor | Request Body / Params | Response Body | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/wms/warehouses` | `WAREHOUSE`, `OPERATOR`, `MANAGER`, `ADMIN` | Status filter | `List<WarehouseResponse>` | Danh sách các kho hàng đang hoạt động |
| `POST` | `/api/v1/wms/warehouses` | `MANAGER`, `ADMIN` | `CreateWarehouseRequest` | `WarehouseResponse` | Tạo kho vật liệu mới |
| `GET` | `/api/v1/wms/materials` | `WAREHOUSE`, `OPERATOR`, `MANAGER`, `ADMIN` | Category, Color, Status | `Page<MaterialResponse>` | Danh sách vật liệu in (PLA, ABS, Resin) |
| `POST` | `/api/v1/wms/materials` | `MANAGER`, `ADMIN` | `CreateMaterialRequest` | `MaterialResponse` | Khai báo mã vật liệu mới |
| `GET` | `/api/v1/wms/inventory` | `WAREHOUSE`, `OPERATOR`, `MANAGER`, `ADMIN` | `warehouseId`, `materialId` | `List<InventoryBalanceResponse>` | Số lượng tồn kho tức thời theo kho |
| `POST` | `/api/v1/wms/receipts` | `WAREHOUSE`, `MANAGER`, `ADMIN` | `CreateStockReceiptRequest` | `StockReceiptResponse` | Nhập kho vật liệu theo lô (Batch/Lot) |
| `POST` | `/api/v1/wms/issues` | `WAREHOUSE`, `MANAGER`, `ADMIN` | `CreateStockIssueRequest` | `StockIssueResponse` | Xuất kho phục vụ in ấn hoặc thanh lý |
| `GET` | `/api/v1/wms/ledger` | `WAREHOUSE`, `MANAGER`, `ADMIN` | Pageable, Date range | `Page<StockLedgerResponse>` | Sổ nhật ký xuất nhập tồn chi tiết |

---

## 12. Non-existent Modules (Status: NOT FOUND)

> [!CAUTION]
> **Booking / Passenger Transportation Module: NOT FOUND**
> Trong source code hiện tại của Memento3D **hoàn toàn không có** các controller:
> - `BookingController`
> - `DriverTripController`
> - `DriverLocationController`
> Memento3D thuần túy là hệ thống sản xuất chế tác 3D cá nhân hóa (Photogrammetry 3D Printing). Các Actor `PROVIDER` và `DRIVER` nếu tích hợp trong tương lai sẽ phục vụ khâu **giao nhận thành phẩm mô hình 3D (Last-mile delivery)** chứ không phải chở khách.

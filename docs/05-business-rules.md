# MEMENTO3D — BUSINESS RULES & VALIDATION CATALOG

## 1. Authentication & User Rules

### BR-AUTH-001: Unique Email Constraint
- **Condition**: Đăng ký tài khoản mới hoặc tạo tài khoản qua OAuth2 Google.
- **Action**: Hệ thống kiểm tra tính duy nhất của email trong bảng `users`.
- **Exception**: Nếu email đã tồn tại, ném `EmailAlreadyExistsException` (HTTP 409 Conflict).
- **Actor**: `CUSTOMER`, `ANONYMOUS`.
- **Affected DB**: Bảng `users` (Unique Index `uk_users_email`).

### BR-AUTH-002: Dual Token Expiration & Invalidation
- **Condition**: Người dùng gửi yêu cầu API kèm Access Token.
- **Action**: Kiểm tra chữ ký HMAC-SHA256 và hạn dùng `exp`. Nếu hết hạn, trả về HTTP 401. Khi logout, token được nạp vào Redis Blacklist với TTL bằng thời gian sống còn lại.
- **Exception**: Token không hợp lệ hoặc nằm trong blacklist trả về HTTP 401 Unauthorized.
- **Actor**: All Actors.

---

## 2. 3D Scan & Photogrammetry Rules

### BR-SCAN-001: Minimum Photo Count & File Constraints
- **Condition**: Khách hàng tải ảnh lên cho một phiên tái tạo 3D (`/api/v1/scans/upload`).
- **Action**: Hệ thống kiểm tra:
  - Số lượng ảnh tối thiểu: `20` ảnh.
  - Số lượng ảnh tối đa: `200` ảnh.
  - Định dạng hợp lệ: `JPEG`, `JPG`, `PNG`, `HEIC`.
  - Dung lượng mỗi ảnh: Tối đa `25MB`.
- **Exception**: Ném `InvalidScanUploadException` (HTTP 400 Bad Request) nếu không thỏa mãn.
- **Actor**: `CUSTOMER`, `ADMIN`.
- **Affected DB**: Bảng `scans`, `scan_images`.

### BR-SCAN-002: Mandatory QC Gate Before AI Reconstruction
- **Condition**: Phiên scan tải lên hoàn tất (`status = PENDING_REVIEW`).
- **Action**: Hệ thống KHÔNG tự động gửi sang AI Reconstruction ngay lập tức. Operator hoặc Manager bắt buộc phải xem xét và duyệt QC (`/api/v1/scans/qc/{id}/approve`).
- **Exception**: Nếu ảnh mờ, sai góc hoặc thiếu sáng, Operator từ chối (`REJECTED`) kèm lý do để khách hàng chụp lại.
- **Actor**: `OPERATOR`, `MANAGER`, `ADMIN`.
- **Affected DB**: Bảng `scans` (status: `PENDING_REVIEW` -> `APPROVED` hoặc `REJECTED`).

### BR-SCAN-003: Model Output Ownership & Access Control
- **Condition**: Người dùng yêu cầu xem trước hoặc tải file 3D (`/api/v1/models/{id}`).
- **Action**: `ScanAccessGuard` xác thực quyền sở hữu: chỉ có chủ nhân của bản scan (`owner_id == authenticated_user_id`) hoặc người dùng có vai trò `ROLE_ADMIN`, `ROLE_MANAGER` mới có quyền truy cập.
- **Exception**: Ném `AccessDeniedException` (HTTP 403 Forbidden).
- **Actor**: `CUSTOMER`, `ADMIN`, `MANAGER`.

---

## 3. Order Management Rules

### BR-ORDER-001: Immutable Order After Production Starts
- **Condition**: Khách hàng yêu cầu hủy đơn hàng hoặc thay đổi cấu hình in.
- **Action**: Hệ thống kiểm tra trạng thái đơn hàng (`order.status`).
  - Cho phép hủy: Khi `status` là `PENDING_PAYMENT`, `PAID`, hoặc `CONFIRMED`.
  - Cấm hủy / cấm sửa: Khi `status` đã chuyển sang `PRINTING`, `QUALITY_CHECK`, hoặc `COMPLETED`.
- **Exception**: Ném `IllegalOrderStateException` (HTTP 400 Bad Request).
- **Actor**: `CUSTOMER`.
- **Affected DB**: Bảng `orders`.

### BR-ORDER-002: Order Price Calculation Consistency
- **Condition**: Khách hàng khởi tạo đơn hàng từ 3D Model Output.
- **Action**: Tổng giá trị đơn hàng được tính toán độc lập tại Backend theo công thức:
  $$\text{Total Price} = (\text{Volume } cm^3 \times \text{Material Density} \times \text{Unit Cost}) + \text{Machine Time Fee} + \text{Base QC Fee} + \text{Shipping Fee}$$
  Không bao giờ nhận trực tiếp giá tiền do Client gửi lên trong Request Body.
- **Exception**: Request chứa giá tiền bị bỏ qua hoặc ném lỗi nếu sai lệch so với Backend tính toán.
- **Actor**: `CUSTOMER`.

---

## 4. Payment Rules (MoMo & ZaloPay)

### BR-PAY-001: Webhook Signature Verification
- **Condition**: Cổng thanh toán (MoMo/ZaloPay) gọi Webhook IPN thông báo kết quả giao dịch.
- **Action**: Backend tính toán lại mã HMAC-SHA256 signature dựa trên secret key và so khớp với trường `signature` (MoMo) hoặc `mac` (ZaloPay) trong payload.
- **Exception**: Nếu chữ ký sai lệch, từ chối xử lý và trả về HTTP 400 với thông báo `INVALID_SIGNATURE`.
- **Actor**: Payment Gateways.
- **Affected DB**: Bảng `payment_transactions`.

### BR-PAY-002: Idempotent Payment Processing
- **Condition**: Cổng thanh toán gửi nhiều lần Webhook IPN cho cùng một mã giao dịch (`orderId` / `transId`).
- **Action**: Backend kiểm tra trạng thái hiện tại của `payment_transactions`:
  - Nếu đã là `SUCCESS` hoặc `FAILED`, bỏ qua cập nhật và lập tức trả về HTTP 200 OK cho Gateway.
  - Ngăn chặn hoàn toàn việc cộng tiền hoặc chuyển trạng thái đơn hàng trùng lặp (Double processing).
- **Actor**: Payment Gateways.

---

## 5. 3D Printing & OctoPrint Rules

### BR-PRINT-001: Single Active Job Per Printer
- **Condition**: Gán một lệnh in mới (`PrintJob`) cho máy in 3D.
- **Action**: Hệ thống kiểm tra trạng thái máy in (`printer.status`). Máy in chỉ được nhận lệnh in khi trạng thái là `IDLE` hoặc `ONLINE`.
- **Exception**: Nếu máy in đang `PRINTING`, `OFFLINE`, hoặc `MAINTENANCE`, ném `PrinterBusyException` (HTTP 409 Conflict).
- **Actor**: `OPERATOR`, `MANAGER`, `ADMIN`.
- **Affected DB**: Bảng `printers`, `print_jobs`.

### BR-PRINT-002: OctoPrint IoT Heartbeat Timeout
- **Condition**: Máy in 3D kết nối thông qua IoT Agent (`X-Agent-Token`).
- **Action**: Scheduled Job quét các máy in không gửi telemetry hoặc heartbeat trong vòng `60 giây` sẽ tự động chuyển trạng thái máy in thành `OFFLINE`.
- **Actor**: Background Scheduler (`PrinterHeartbeatScheduler`).
- **Affected DB**: Bảng `printers` (cột `status`, `last_seen_at`).

---

## 6. Warehouse & Inventory Rules (WMS)

### BR-WMS-001: Non-negative Stock Balance
- **Condition**: Thực hiện lệnh xuất kho (`StockIssue`) hoặc trừ vật liệu tiêu hao lệnh in.
- **Action**: Hệ thống kiểm tra số lượng khả dụng trong kho (`inventory_balances.quantity`).
- **Exception**: Nếu số lượng xuất vượt quá tồn kho khả dụng, ném `InsufficientStockException` (HTTP 400 Bad Request). Tuyệt đối không cho phép tồn kho âm.
- **Actor**: `WAREHOUSE`, `OPERATOR`, `MANAGER`, `ADMIN`.
- **Affected DB**: Bảng `inventory_balances`, `stock_ledger`.

### BR-WMS-002: Mandatory Audit Ledger on Every Stock Movement
- **Condition**: Bất kỳ hành động nhập kho (`RECEIPT`), xuất kho (`ISSUE`), điều chỉnh (`ADJUSTMENT`), hoặc khấu trừ in ấn (`PRINT_CONSUMPTION`) diễn ra.
- **Action**: Phải ghi đồng thời 1 bản ghi vào bảng `stock_ledger` bao gồm: `warehouse_id`, `material_id`, `transaction_type`, `quantity_change`, `balance_before`, `balance_after`, `created_by`, `reference_id`.
- **Actor**: All Warehouse Operations.
- **Affected DB**: Bảng `stock_ledger`.

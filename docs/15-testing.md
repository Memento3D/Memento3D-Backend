# MEMENTO3D — TESTING STRATEGY & TEST SPECIFICATIONS

## 1. Overview & Testing Philosophy
Hệ thống Memento3D áp dụng mô hình kim tự tháp kiểm thử (Test Pyramid) nhằm đảm bảo tính ổn định cao nhất cho các nghiệp vụ thanh toán, khấu trừ kho và điều khiển phần cứng máy in 3D:

1. **Unit Tests (70%)**: Kiểm thử các hàm tính toán giá đơn hàng, xác thực chữ ký HMAC-SHA256, chuyển trạng thái máy in, logic trừ kho không âm.
2. **Integration Tests (20%)**: Sử dụng Testcontainers / H2 Database để kiểm thử toàn diện Repository, `@Transactional` rollback, và Flyway migrations.
3. **API / Security Tests (10%)**: Sử dụng `MockMvc` hoặc `@SpringBootTest(webEnvironment = RANDOM_PORT)` để kiểm thử các bộ lọc JWT, RBAC phân quyền và Global Exception Handler.

---

## 2. Test Matrix Across Modules

| Module | Unit Tests | Integration Tests | Security / RBAC | Status Hiện Tại |
|---|:---:|:---:|:---:|---|
| **Authentication** | Có (JWT, Passwords) | Có (User Repo) | Có (Filter 401/403) | **IMPLEMENTED** |
| **Scans & Upload** | Có (File count, type) | Có (Scan Repo) | Có (ScanAccessGuard) | **IMPLEMENTED** |
| **Scan QC** | Có (Status transition)| Có (QC Flow) | Có (Operator only) | **IMPLEMENTED** |
| **Orders** | Có (Pricing engine) | Có (Order Repo) | Có (Owner check) | **PARTIAL** |
| **Payments** | Có (HMAC signature) | Có (Idempotency) | Có (Public Webhook) | **IMPLEMENTED** |
| **Printers & IoT** | Có (Status machine) | Mock (Agent stream)| Có (X-Agent-Token) | **IMPLEMENTED** |
| **Print Jobs** | Có (Consumption) | Có (Job Repo) | Có (Operator/Manager)| **PARTIAL** |
| **WMS Inventory** | Có (Ledger, Balance) | Có (WMS Repo) | Có (Warehouse role) | **IMPLEMENTED** |

---

## 3. Running Tests via Command Line

### 3.1 Backend Tests (JUnit 5 + Mockito)
```powershell
cd Memento3D-Backend\memeto3D

# Chạy toàn bộ Unit Tests
.\mvnw test

# Chạy một lớp kiểm thử cụ thể
.\mvnw test -Dtest=AuthenticationServiceTest

# Chạy kiểm thử thanh toán MoMo / ZaloPay
.\mvnw test -Dtest=PaymentServiceTest
```

### 3.2 Frontend Tests (Vitest / Jest)
```powershell
cd Memento3D-Fronted-Web

# Chạy Unit Tests giao diện
npm test

# Chạy kiểm thử kèm thống kê độ bao phủ (Coverage)
npm test -- --coverage
```

---

## 4. Priority Test Cases To Implement (Missing Tests)

> [!IMPORTANT]
> **TC-PAY-001: Concurrent Webhook Attack (Race Condition)**
> - **Mục tiêu**: Giả lập 10 request Webhook IPN từ MoMo cùng 1 `transId` bắn đồng thời tới server trong 1 miligiây.
> - **Kỳ vọng**: Chỉ duy nhất 1 request xử lý thành công chuyển order sang `PAID`, 9 request còn lại nhận phản hồi HTTP 200 mà không cập nhật lại DB.

> [!IMPORTANT]
> **TC-WMS-002: Over-issue Prevention (Negative Stock)**
> - **Mục tiêu**: Giả lập 2 kỹ thuật viên cùng bấm hoàn thành lệnh in tiêu tốn 800g filament trong khi tồn kho chỉ còn 1000g (Tổng nhu cầu 1600g > 1000g).
> - **Kỳ vọng**: Giao dịch thứ nhất thành công (tồn còn 200g), giao dịch thứ hai bị từ chối với `InsufficientStockException`.

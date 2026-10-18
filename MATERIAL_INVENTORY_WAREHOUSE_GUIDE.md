# Memento3D — Material, Inventory & Warehouse Management Guide

## 1. Overview
Phân hệ quản lý vật liệu in 3D, hệ thống kho bãi, số dư tồn kho, lệnh in và sổ cái biến động (Immutable Ledger) cho nền tảng Memento3D.

Tech stack:
- Java 17 + Spring Boot 3.4 / 4.x
- Spring Data JPA + Hibernate
- PostgreSQL 16+
- Flyway Migration `V8__create_materials_warehouses_inventory_and_print_jobs.sql`
- Bean Validation
- Spring Security (JWT Stateless, Role-based `ADMIN`, `OPERATOR`)

---

## 2. Core Invariants & Business Formulas

### **1. Tồn kho khả dụng (Available Gram)**
$$\text{availableGram} = \text{onHandGram} - \text{reservedGram}$$
- Ràng buộc DB: `CHECK (on_hand_gram >= reserved_gram)` và `CHECK (reserved_gram >= 0)`.
- Mọi thao tác xuất kho hoặc giữ kho vượt quá `availableGram` đều bị từ chối với lỗi HTTP 409 `INSUFFICIENT_STOCK`.

### **2. Đối soát tiêu hao lệnh in (Print Reconciliation)**
$$\text{issuedGram} = \text{usedGram} + \text{wasteGram} + \text{returnedGram}$$
- Sau khi in hoàn tất hoặc thất bại, lượng vật liệu hoàn trả (`returnedGram`) tự động được nhập lại vào kho thông qua giao dịch `PRINT_RETURN`.

### **3. Bảo vệ vô hiệu hóa kho (Warehouse Deactivate Guard)**
- Ngăn chặn vô hiệu hóa kho (`status = INACTIVE`) nếu `onHandGram > 0` (lỗi `WAREHOUSE_HAS_STOCK`) hoặc `reservedGram > 0` (lỗi `WAREHOUSE_HAS_RESERVATIONS`).

---

## 3. Database Schema (Flyway V8)

1. `materials`: SKU (unique, lower-case), type, color, density, diameter, temperature ranges, unit cost, status (`ACTIVE`, `INACTIVE`), audit fields.
2. `warehouses`: Code (unique, lower-case), name, location, status (`ACTIVE`, `INACTIVE`), audit fields.
3. `inventory_balances`: Warehouse & Material (unique pair), `on_hand_gram`, `reserved_gram`, optimistic locking (`version`).
4. `stock_receipts`: Goods receipt with idempotency key, quantity, unit cost, batch number.
5. `stock_issues`: Manual issue with idempotency key, quantity, reason.
6. `print_jobs`: Job number, order link, printer link, material, estimated grams, lifecycle status.
7. `stock_reservations`: Material hold for print job.
8. `print_job_consumptions`: Issued, used, waste, returned grams and reconciliation status.
9. `inventory_transactions`: Immutable transaction ledger recording all balance deltas.

---

## 4. Endpoints Implemented in Sprint 1

### **Materials (`/api/v1/materials`)**
- `POST /api/v1/materials` – Đăng ký vật liệu mới.
- `GET /api/v1/materials` – Danh sách vật liệu (phân trang, search, lọc theo type/status).
- `GET /api/v1/materials/{id}` – Chi tiết vật liệu.
- `PATCH /api/v1/materials/{id}` – Cập nhật thông số kỹ thuật.
- `DELETE /api/v1/materials/{id}` – Vô hiệu hóa vật liệu (`status = INACTIVE`).

### **Warehouses (`/api/v1/warehouses`)**
- `POST /api/v1/warehouses` – Đăng ký kho xưởng mới.
- `GET /api/v1/warehouses` – Danh sách kho xưởng (search, status filter).
- `GET /api/v1/warehouses/{id}` – Chi tiết kho xưởng.
- `PATCH /api/v1/warehouses/{id}` – Chỉnh sửa thông tin kho.
- `DELETE /api/v1/warehouses/{id}` – Vô hiệu hóa kho (bảo vệ nếu còn tồn kho/reservation).

---

## 5. Automated Tests
Chạy kiểm thử:
```bash
.\mvnw.cmd test "-Dtest=MaterialServiceTest,WarehouseServiceTest"
```
Kết quả: 10/10 test cases passed.

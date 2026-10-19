# 🚀 Memento3D Backend — Event-Driven Architecture & N+1 Optimization Guide

Tài liệu hướng dẫn kỹ thuật chi tiết dành cho kỹ sư Backend về kiến trúc Kafka Event-Driven, Transactional Outbox, Server-Sent Events và các nguyên tắc loại bỏ N+1 Hibernate SQL queries trong Spring Boot 4.x / Hibernate 7.

---

## 1. Kiến Trúc Sự Kiện (Event-Driven Architecture)

### 1.1. Luồng Hoạt Động (Flow)
1. **Lưu nghiệp vụ:** Trong @Transactional, nghiệp vụ lưu entity vào cơ sở dữ liệu và đồng thời gọi `outboxPublisher.publish(...)` để lưu vào bảng `transactional_outbox` với status `PENDING`.
2. **Relay ra Kafka:** `OutboxRelayScheduler` quét bảng `transactional_outbox` định kỳ (1 giây), gửi sang Apache Kafka (`memento3d.events`, `memento3d.order.status`, `memento3d.printjob.status`, `memento3d.reconstruction.status`, `memento3d.wms.inventory`) và cập nhật status sang `SENT`.
3. **Idempotent Consumer:** `KafkaEventConsumer` nhận message, kiểm tra bảng `processed_events`. Nếu `eventId` chưa tồn tại, consumer xử lý và chuyển tiếp sang `SseEmitterManager`.
4. **SSE Client Push:** `SseEmitterManager` chuyển tiếp realtime event tới client đang kết nối tại `/api/v1/events/stream`.

### 1.2. Các Event Chính Trong Hệ Thống

| Event Type | Aggregate Type | Mô Tả Nghiệp Vụ |
| :--- | :--- | :--- |
| `ORDER_PAID` / `ORDER_CONFIRMED` | `ORDER` | Kích hoạt cấp quyền (Entitlement) tải file 3D số hoặc đẩy vào hàng đợi in vật lý |
| `PRINT_JOB_STATUS_CHANGED` | `PRINT_JOB` | Thông báo tiến trình in từ máy in thật (Prusa/BambuLab) tới quản lý và khách hàng |
| `RECONSTRUCTION_COMPLETED` | `RECONSTRUCTION_JOB` | Báo hoàn thành xử lý 3D SfM/MVS, tạo model version sẵn sàng cho khách hàng review |
| `STOCK_RECEIPT_COMPLETED` | `WMS_INVENTORY` | Cập nhật số dư kho tức thời sau khi quản lý duyệt phiếu nhập hàng |
| `STOCK_ISSUE_COMPLETED` | `WMS_INVENTORY` | Giảm tồn kho khả dụng sau khi xuất kho cho lệnh in hoặc chuyển kho |

---

## 2. Các Quy Chuẩn Loại Bỏ N+1 SQL Queries

### 2.1. Quy tắc ánh xạ DTO từ Entity
- **Không tự ý gọi `.getCollection()` trong DTO mapper danh sách:**
  Khi mapper được dùng cho cả chi tiết và danh sách (ví dụ: `OrderResponse.fromEntity`), luôn kiểm tra:
  ```java
  if (order.getItems() != null && org.hibernate.Hibernate.isInitialized(order.getItems())) {
      // Map items
  } else {
      // Trả về empty list hoặc null để không kích hoạt Lazy load query
  }
  ```
- **Sử dụng DTO Projection hoặc `@EntityGraph` cho endpoint chi tiết:**
  Khi cần nạp cây đối tượng sâu (`Order -> OrderItems -> Product -> Category`), khai báo:
  ```java
  @EntityGraph(attributePaths = {"items", "items.product", "items.product.category"})
  Optional<Order> findWithItemsById(UUID id);
  ```

### 2.2. Kiểm thử hiệu năng (Regression Tests)
Chạy bộ kiểm thử tự động đo lường câu lệnh SQL qua Hibernate Statistics:
```bash
./mvnw test "-Dtest=JpaQueryOptimizationPerformanceTest" "-Djacoco.skip=true"
```
Đảm bảo tất cả các query phân trang danh sách (PrintJobs, Orders, Products, WMS Receipts/Issues) đều giữ số lượng SQL không đổi ($O(1)$) là 2 câu lệnh (1 select + 1 count).

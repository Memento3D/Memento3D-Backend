# Memento3D — Backend Service

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-blue.svg)](https://postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-V1--V15-red.svg)](https://flywaydb.org/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-Event--Driven-black.svg)](https://kafka.apache.org/)
[![VNPAY](https://img.shields.io/badge/VNPAY-Sandbox%20v2.1.0-blue.svg)](https://sandbox.vnpayment.vn/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.0%20%2F%20Swagger-green.svg)](http://localhost:8080/swagger-ui/index.html)

---

## 📌 1. Tổng Quan (Overview)

**Memento3D Backend** là máy chủ trung tâm xử lý dữ liệu và điều phối nghiệp vụ cho toàn bộ hệ sinh thái **Memento3D** – nền tảng tạo mô hình 3D từ hình ảnh/video photogrammetry, thương mại điện tử sản phẩm in 3D cá nhân hóa và quản lý xưởng in thông minh (WMS). 

Dịch vụ được thiết kế theo mô hình **Domain-Driven Modular Monolith & Clean Architecture**, xây dựng trên nền tảng **Java 21 LTS** và **Spring Boot 4.1.x**, kết hợp với **PostgreSQL (Neon Cloud Serverless)**, **Redis 7**, **Apache Kafka**, và **Cloudinary**.

---

## 🚀 2. Các Phân Hệ & Tính Năng Cốt Lõi

### 🔐 2.1. Xác Thực Bảo Mật & 7 Tác Nhân (RBAC 7 Roles)
- **Stateless JWT Dual-Token:** Access token ngắn hạn (15-60 phút) mã hóa HMAC-SHA256, kết hợp Refresh Token lưu database có mã hóa.
- **Refresh Token Rotation (Family Detection):** Tự động hủy toàn bộ phiên làm việc (Token Family) nếu phát hiện hành vi tái sử dụng Refresh Token đã thu hồi.
- **Redis Token Blacklist:** Đưa token vào danh sách đen tức thì khi người dùng đăng xuất hoặc đổi mật khẩu.
- **Hệ thống 7 Vai Trò (7 Actors):**
  - `ROLE_USER` / `ROLE_CUSTOMER`: Khách hàng mua sắm, đặt in 3D, quản lý phiên scan.
  - `ROLE_MANAGER`: Quản lý vận hành xưởng, phê duyệt đơn hàng, xác nhận tiền mặt, phân bổ kho.
  - `ROLE_ADMIN`: Quản trị viên cấp cao nhất, toàn quyền phân quyền và giám sát hệ thống.
  - `ROLE_WAREHOUSE`: Thủ kho WMS, quản lý nhập/xuất kho vật liệu theo số lô.
  - `ROLE_OPERATOR`: Kỹ thuật viên xưởng in, kiểm duyệt QC Gate, điều phối máy in 3D.
  - `ROLE_PROVIDER`: Đối tác sản xuất gia công ngoài B2B.
  - `ROLE_DRIVER`: Tài xế giao vận, quản lý trạng thái trực tuyến và xác nhận giao hàng.
- **Google OAuth2 & OIDC:** Đăng nhập một chạm bằng Google Account, tự động liên kết tài khoản an toàn qua cookie bảo mật ngắn hạn.

### 💳 2.2. Cổng Thanh Toán Đa Kênh (Payment Subsystem)
- **VNPAY Sandbox (v2.1.0):**
  - Tạo URL thanh toán an toàn với mã băm chữ ký kiểm tra **HMAC-SHA512**.
  - Hỗ trợ thanh toán thẻ ATM nội địa (NCB...), Internet Banking và quét mã VNPAY-QR.
  - Webhook IPN xử lý bất đồng bộ, đảm bảo tính Idempotent và cập nhật trạng thái đơn hàng tức thì.
- **ZaloPay Sandbox:**
  - Tích hợp ZaloPay API v2 với chữ ký bảo mật HMAC-SHA256 (`key1` tạo đơn, `key2` xác minh callback).
- **Tiền mặt (CASH):**
  - Hỗ trợ đơn hàng COD hoặc nhận hàng thanh toán tại xưởng, có luồng phê duyệt từ Quản lý / Tài xế.

### 📧 2.3. Động Cơ Thông Báo Tự Động (Notifications Engine)
- **Email Thông Báo Tự Động (Spring Mail + Thymeleaf):**
  - Gửi email định dạng HTML chuyên nghiệp khi đơn hàng được xác nhận (`ORDER_CONFIRMED`), hoàn tất sản xuất (`ORDER_COMPLETED`), hoặc bắt đầu giao hàng (`ORDER_SHIPPED`).
  - Gửi email đặt lại mật khẩu bảo mật (Password Reset) theo chuẩn NIST SP 800-63B.
- **Server-Sent Events (SSE):**
  - Kênh streaming sự kiện thời gian thực tới trình duyệt web: đẩy thông báo tức thì khi có biến động đơn hàng hoặc kiểm định hoàn tất.

### 🧱 2.4. Quản Lý Vật Liệu & Kho Bãi WMS (F01 - F08)
- **Material Catalog (F01):** Khai báo chủng loại vật liệu (`PLA`, `PETG`, `ABS`, `RESIN`, `TPU`), nhiệt độ đùn, bàn nhiệt, mật độ $g/cm^3$ và giá vốn theo gram ($VND/g$).
- **Warehouse Management (F02):** Quản lý cơ sở kho vật lý với cơ chế **Deactivate Guard** (chặn vô hiệu hóa kho khi còn tồn kho hoặc đang giữ chỗ cho lệnh in).
- **Double-Entry Inventory Ledger (F03 - F08):**
  - Quản lý nhập kho (Goods Receipts) và xuất kho (Stock Issues) theo số lô/cuộn sợi.
  - Công thức tính tồn khả dụng tức thì: $\text{Available} = \text{On-Hand} - \text{Reserved}$.
  - Cơ chế khóa giữ nguyên liệu (`RESERVED`) khi đơn hàng được xác nhận và quyết toán hao hụt thực tế sau khi in xong.

### 🚗 2.5. Quản Lý Đội Ngũ Giao Nhận (Driver Management)
- **Trạng thái tài xế thời gian thực:** Cập nhật trạng thái (`ACTIVE`, `INACTIVE`, `BUSY`, `OFFLINE`).
- **Phân công vận đơn:** Gán kiện hàng thành phẩm cho tài xế, theo dõi lộ trình và xác nhận ký nhận (Proof of Delivery).

### ⚡ 2.6. Tối Ưu Hiệu Năng & Outbox Pattern
- **SafePageRequest:** Tự động chuẩn hóa tham số phân trang (`page`, `size`), giới hạn `size <= 100` để ngăn ngừa tấn công DoS và tràn RAM.
- **Triệt tiêu N+1 Query:** Tối ưu hóa truy vấn dữ liệu quan hệ với Hibernate `JOIN FETCH` và `@EntityGraph`.
- **Redis Caching:** Cache danh mục sản phẩm (`product_categories`), cấu hình hệ thống và danh sách đen JWT.
- **Transactional Outbox & Apache Kafka:** Ghi nhận sự kiện vào bảng `outbox_events` trong cùng database transaction, sau đó publish tới Kafka để đảm bảo tính nhất quán cuối cùng (Eventual Consistency).

---

## 🏗️ 3. Cấu Trúc Mã Nguồn (Package-by-Domain Architecture)

```text
Memento3D-Backend/
├── README.md                                   # Tài liệu chính của Backend
├── docker-compose.yml                          # Môi trường chạy PostgreSQL & Redis
└── memeto3D/                                   # Mã nguồn chính Spring Boot
    ├── pom.xml                                 # Maven dependencies & Plugins
    ├── .env.example                            # Khai báo mẫu biến môi trường
    ├── src/
    │   ├── main/
    │   │   ├── java/com/memento3d/             # Kiến trúc phân tách theo Bounded Context
    │   │   │   ├── identity/                   # Xác thực, User, JWT, OAuth2, RBAC
    │   │   │   ├── scans/                      # Quản lý phiên scan & Upload multipart
    │   │   │   ├── quality/                    # Kiểm định chất lượng ảnh (QC Gate)
    │   │   │   ├── reconstruction/             # Điều phối lệnh tái tạo 3D (SfM / MVS)
    │   │   │   ├── models/                     # Quản lý file 3D (GLB/STL/OBJ) & đánh giá
    │   │   │   ├── orders/                     # Đơn hàng in 3D, snapshot sản phẩm
    │   │   │   ├── payment/                    # VNPAY Sandbox, ZaloPay, Webhooks
    │   │   │   ├── warehouse/                  # Vật liệu F01, Cơ sở kho F02, Tồn kho F03-F08
    │   │   │   ├── driver/                     # Trạng thái tài xế & điều phối giao nhận
    │   │   │   ├── notification/               # Email Spring Mail, SSE realtime streaming
    │   │   │   ├── operations/                 # Giám sát máy in 3D & Agent IoT
    │   │   │   └── shared/                     # ApiResponse, GlobalExceptionHandler, Outbox, Redis
    │   │   └── resources/
    │   │       ├── application.yml             # Cấu hình Spring Boot trung tâm
    │   │       ├── db/migration/               # Flyway Migrations (V1 -> V15)
    │   │       └── templates/email/            # Giao diện Email HTML (Thymeleaf templates)
    │   └── test/                               # Bộ kiểm thử Unit & Integration Tests
```

---

## 🛠️ 4. Cấu Hình Biến Môi Trường (Environment Variables)

Tạo file `.env` tại thư mục `Memento3D-Backend/memeto3D/` dựa trên mẫu `.env.example`:

```env
# Server
PORT=8080

# Database (PostgreSQL / Neon Cloud Serverless)
DB_HOST=ep-cool-butterfly-123456.us-east-2.aws.neon.tech
DB_PORT=5432
DB_NAME=memeto3d
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password

# Redis In-Memory Cache
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_TIMEOUT=2000ms

# JWT Security
JWT_SECRET=your-256-bit-secret-key-at-least-32-characters-long
JWT_EXPIRATION=3600000

# Cloudinary Storage
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

# VNPAY Sandbox (v2.1.0)
VNPAY_TMN_CODE=YOUR_VNPAY_TMN_CODE
VNPAY_HASH_SECRET=YOUR_VNPAY_HASH_SECRET
VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:5173/payment/vnpay/return
VNPAY_IPN_URL=http://localhost:8080/api/v1/payments/vnpay/ipn

# ZaloPay Sandbox
ZALOPAY_APP_ID=2554
ZALOPAY_KEY1=your_key_1
ZALOPAY_KEY2=your_key_2
ZALOPAY_ENDPOINT=https://sb-openapi.zalopay.vn/v2/create

# Spring Mail (SMTP Email Notifications)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_password
```

---

## 🚦 5. Hướng Dẫn Khởi Chạy Dự Án

### Yêu cầu hệ thống:
- **Java**: OpenJDK 21 LTS (hoặc 17 LTS)
- **Maven**: 3.9+ (hoặc dùng `.\mvnw` đi kèm)
- **PostgreSQL**: 16+ (hoặc Neon Serverless)
- **Redis**: 7.x

### Khởi chạy môi trường Local:
```powershell
cd Memento3D-Backend\memeto3D

# 1. Compile mã nguồn và bỏ qua test
.\mvnw clean compile -DskipTests

# 2. Khởi chạy máy chủ Spring Boot
.\mvnw spring-boot:run
```

Sau khi khởi chạy thành công:
- **REST API Base URL:** `http://localhost:8080/api/v1`
- **Swagger Interactive API:** `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI Schema (JSON):** `http://localhost:8080/v3/api-docs`

---

## 📡 6. Bảng Tra Cứu API Endpoints Tiêu Biểu

| Nhóm Phân Hệ | Phương Thức | Đường Dẫn Endpoint | Phân Quyền Yêu Cầu | Mục Đích Nghiệp Vụ |
|---|:---:|---|---|---|
| **Identity** | `POST` | `/api/v1/auth/register` | Public | Đăng ký tài khoản khách hàng mới |
| **Identity** | `POST` | `/api/v1/auth/login` | Public | Đăng nhập nhận JWT Token đa cổng |
| **Identity** | `POST` | `/api/v1/auth/refresh` | Public | Cấp mới Access Token qua Rotation |
| **Identity** | `POST` | `/api/v1/auth/logout` | Authenticated | Đăng xuất & thêm token vào Redis Blacklist |
| **Materials** | `GET`, `POST` | `/api/v1/materials` | `MANAGER`, `OPERATOR`, `ADMIN` | Quản lý danh mục vật liệu in 3D (F01) |
| **Warehouses** | `GET`, `POST` | `/api/v1/warehouses` | `MANAGER`, `WAREHOUSE`, `ADMIN` | Quản lý cơ sở kho & Deactivate Guard (F02) |
| **Inventory** | `POST` | `/api/v1/inventory/receipts` | `WAREHOUSE`, `MANAGER` | Nhập kho nguyên vật liệu theo số lô (F04) |
| **Inventory** | `POST` | `/api/v1/inventory/issues` | `WAREHOUSE`, `MANAGER` | Xuất kho sản xuất / hao hụt (F05) |
| **Products** | `GET`, `POST` | `/api/v1/products` | Public (`GET`), `MANAGER` (`POST`) | Danh mục sản phẩm in 3D thương phẩm |
| **Categories** | `GET` | `/api/v1/product-categories` | Public (Redis Cached) | 10 ngành hàng sản phẩm in 3D |
| **Orders** | `POST` | `/api/v1/orders` | `ROLE_USER` / Customer | Khởi tạo đơn hàng in 3D cá nhân hóa |
| **Orders** | `GET`, `PUT` | `/api/v1/orders/{id}` | Authenticated | Truy vấn và cập nhật trạng thái đơn hàng |
| **VNPAY** | `POST` | `/api/v1/payments/vnpay/create` | `ROLE_USER` / Customer | Sinh URL thanh toán VNPAY Sandbox |
| **VNPAY** | `GET` | `/api/v1/payments/vnpay/ipn` | VNPAY Gateway | Tiếp nhận Webhook IPN đối soát giao dịch |
| **Driver** | `GET`, `PUT` | `/api/v1/driver/status` | `ROLE_DRIVER` | Quản lý trạng thái trực tuyến của tài xế |
| **Driver** | `GET` | `/api/v1/driver/deliveries` | `ROLE_DRIVER` | Danh sách kiện hàng đang phụ trách giao |
| **Notifications** | `GET` | `/api/v1/notifications/stream` | Authenticated | Luồng Server-Sent Events (SSE) thời gian thực |
| **Scans** | `POST` | `/api/v1/scans` | `ROLE_USER` / Customer | Khởi tạo phiên scan photogrammetry mới |
| **Uploads** | `POST` | `/api/v1/uploads/{id}/chunks/{idx}` | `ROLE_USER` / Customer | Tải lên từng chunk file ảnh/video nặng |

---

## 🧪 7. Kiểm Thử & Đo Độ Phủ Code (Testing & JaCoCo)

```powershell
cd Memento3D-Backend\memeto3D

# Chạy toàn bộ Unit & Integration Tests
.\mvnw test
```

Mở báo cáo độ phủ mã nguồn: `target/site/jacoco/index.html` trên trình duyệt để kiểm tra tỷ lệ Line & Branch coverage.

---

## 👥 8. Đội Ngũ Phát Triển & Bản Quyền
- **Technical Lead & Backend Architect:** Lê Tuấn Kiệt ([KietLT33](https://github.com/KietLT33))
- Dự án được bảo vệ bản quyền bởi **Memento3D Engineering Team**.

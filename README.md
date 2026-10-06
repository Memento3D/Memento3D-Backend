# Memento3D - Backend Service

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4%2F4.1-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2B-blue.svg)
![Redis](https://img.shields.io/badge/Redis-7.0-red.svg)
![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)

## 📌 Tổng Quan (Overview)

**Memento3D Backend** (3DForge Backend) là dịch vụ máy chủ trung tâm xử lý dữ liệu cho hệ sinh thái **Memento3D** – nền tảng tạo mô hình 3D từ hình ảnh/video photogrammetry. Dịch vụ được xây dựng trên nền tảng **Java 17** và **Spring Boot**, kết hợp với **PostgreSQL**, **Redis**, và **Cloudinary** để cung cấp các API hiệu năng cao, bảo mật và mở rộng.

---

## 🚀 Các Tính Năng Chính (Key Features)

### 1. 🔐 Authenticate & Authorization
- **JWT Security**: Xác thực token không lưu vết (stateless JWT access token) cùng với cơ chế xoay vòng và thu hồi refresh token.
- **Token Blacklist**: Lưu danh sách đen JWT đã thu hồi/đăng xuất trong Redis và database.
- **Phân quyền người dùng (RBAC)**: Hỗ trợ các vai trò `user`, `admin`, và `operator`.

### 2. 📸 Scan Session & Upload Management
- **Quản lý phiên Scan**: Tạo, cập nhật, phân trang và truy vấn danh sách scan của người dùng.
- **Tải lên từng phần (Chunked Multipart Upload)**: Hỗ trợ Resume/Retry cho các file dung lượng lớn thông qua `/api/v1/uploads`.
- **Cloud Storage Integration**: Tích hợp với Cloudinary API cho việc lưu trữ và quản lý tài nguyên phương tiện.

### 3. 🔬 Engine Kiểm Tra Chất Lượng Ảnh (Quality Check Engine)
- **Phân tích kỹ thuật chi tiết**: Đánh giá độ sắc nét (sharpness/blur), độ sáng/phơi sáng (brightness/exposure), số lượng đặc trưng (feature count), độ chồng lấp (overlap ratio).
- **Phát hiện ảnh trùng lặp & nhiễu**: Nhận diện ảnh trùng lặp tuyệt đối/tương đối (hash MD5/perceptual) và phân tích nhiễu (SNR score).
- **Quality Runs**: Lưu vết và cung cấp API truy vấn kết quả kiểm định kỹ thuật chi tiết theo từng ảnh.

### 4. ⚙️ Tiến Trình Tái Tạo 3D (3D Reconstruction Pipeline)
- **Hàng chờ và Trạng thái**: Theo dõi trạng thái từng giai đoạn (`queued`, `extracting`, `detecting`, `matching`, `sfm`, `mvs`, `meshing`, `texturing`, `repairing`, `exporting`, `done`).
- **Đánh giá khả năng in 3D (Printability Check)**: Kiểm tra lưới 3D, watertight status, lỗ hổng và cạnh phi manifold.

### 5. ⭐ Đánh Giá Mô Hình (Model Rating System)
- **Model Quality Feedback**: Cho phép người dùng và khách xem trước gửi đánh giá (1 - 5 sao) và bình luận cho mô hình 3D đã khởi tạo.
- **Upsert Rating Logic**: Tự động cập nhật đánh giá nếu người dùng đã đánh giá phiên scan đó trước đó.

### 6. 🔄 Transactional Outbox & Kafka Event Streaming
- **Outbox Pattern**: Ghi nhận các sự kiện hệ thống (`scan.events`, `qc.events`, `reconstruction.events`, `order.events`) đảm bảo tính toàn vẹn dữ liệu.
- **Dead Letter Queue (DLQ)**: Quản lý và retry các sự kiện xử lý thất bại.

---

## 🏗️ Cấu Trúc Dự Án (Project Structure)

```
Memento3D-Backend/
├── README.md                           # Documentation chính của Backend
├── docker-compose.yml                  # File Docker Compose môi trường dev/staging
└── memeto3D/                           # Source code chính ứng dụng Spring Boot
    ├── db/
    │   └── init_schema.sql             # SQL script khởi tạo PostgreSQL Database Schema
    ├── doc/
    │   └── BE_GUIDELINES.md            # Quy tắc lập trình & tiêu chuẩn Code / Test / DB
    ├── src/
    │   ├── main/
    │   │   ├── java/com/memeto/team/memeto3d/
    │   │   │   ├── config/             # Cấu hình Security, OpenAPI, Cloudinary, Redis
    │   │   │   ├── controller/         # REST Controllers (Auth, Scan, QualityRun, ModelRating, Upload)
    │   │   │   ├── dto/                # Data Transfer Objects (Requests & Responses)
    │   │   │   ├── entity/             # JPA Entities
    │   │   │   ├── exception/          # Custom Exception & Global Exception Handler
    │   │   │   ├── repository/         # Spring Data JPA Repositories
    │   │   │   ├── security/           # JWT Filter & UserDetails Implementation
    │   │   │   └── service/            # Business Logic Services & Interfaces
    │   │   └── resources/              # application.properties / application.yml
    │   └── test/                       # Unit tests & Integration tests
    ├── Dockerfile                      # Multi-stage build Dockerfile
    ├── docker-compose.yml              # Docker Compose cho app + postgres + redis
    ├── pom.xml                         # Maven dependencies & JaCoCo coverage plugin
    └── .env.example                    # Mẫu khai báo biến môi trường
```

---

## 🛠️ Biến Môi Trường (Environment Variables)

Sao chép file `.env.example` thành `.env` trong thư mục `memeto3D/` và điền cấu hình phù hợp:

```env
# Server
PORT=8080

# Database (PostgreSQL / NeonDB)
DB_HOST=localhost
DB_PORT=5431
DB_NAME=memeto3d
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Cloudinary Storage
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-minimum-256-bits
JWT_EXPIRATION=3600000

# JPA/Hibernate
DDL_AUTO=update

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

# Redis Cache & Token Blacklist
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_TIMEOUT=2000ms
```

---

## 🚦 Hướng Dẫn Chạy Dự Án (Getting Started)

### Yêu cầu hệ thống:
- **Java**: JDK 17 trở lên
- **Maven**: 3.8+ (hoặc dùng `mvnw` / `mvnw.cmd` có sẵn)
- **Docker & Docker Compose** (nếu chạy container)
- **PostgreSQL**: Version 16+
- **Redis**: Version 7+

### 1. Khởi chạy bằng Maven (Local Development)

1. Khởi động cơ sở dữ liệu PostgreSQL và Redis (hoặc dùng docker-compose dịch vụ phụ trợ):
   ```bash
   docker compose up -d postgres redis
   ```
2. Thực thi script khởi tạo CSDL: `memeto3D/db/init_schema.sql`
3. Chạy ứng dụng Spring Boot:
   ```bash
   cd memeto3D
   ./mvnw spring-boot:run
   ```
   *(Hoặc trên Windows PowerShell: `.\mvnw.cmd spring-boot:run`)*

### 2. Khởi chạy bằng Docker Compose (Full Container Stack)

```bash
cd memeto3D
docker compose up -d --build
```
Dịch vụ backend sẽ lắng nghe tại cổng `http://localhost:8080`.

---

## 🧪 Kiểm Thử & Đo Độ Phủ Code (Testing & JaCoCo Coverage)

Dự án áp dụng quy định kiểm thử nghiêm ngặt theo [BE_GUIDELINES.md](file:///c:/Users/KIETLT/Documents/GitHub/Memento3d/Memento3D-Backend/memeto3D/doc/BE_GUIDELINES.md):
- **C0 Coverage (Line Coverage)**: Mục tiêu 100%
- **C1 Coverage (Branch Coverage)**: Minimum ≥ 85%

### Chạy Unit Test và tạo báo cáo Coverage:
```powershell
# Trên Windows PowerShell
cd memeto3D
.\mvnw.cmd test
```

### Xem Báo Cáo JaCoCo:
Mở file `target/site/jacoco/index.html` trên trình duyệt để kiểm tra chi tiết độ phủ theo từng package/class.

---

## 📖 Tài Liệu API (API Documentation)

Ứng dụng tích hợp **Springdoc OpenAPI (Swagger UI)**. Khi ứng dụng đang chạy, bạn có thể truy cập tài liệu tương tác tại:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI Spec (JSON)**: `http://localhost:8080/v3/api-docs`

### Một số Endpoint tiêu biểu:

| Nhóm Endpoint | Đường dẫn (Path) | Phương thức | Mô tả |
|---|---|---|---|
| **Auth** | `/api/auth/register` | `POST` | Đăng ký tài khoản người dùng mới |
| **Auth** | `/api/auth/login` | `POST` | Đăng nhập và nhận JWT Token |
| **Scans** | `/api/v1/scans` | `GET` | Lấy danh sách phiên scan (có phân trang & lọc) |
| **Scans** | `/api/v1/scans` | `POST` | Khởi tạo phiên scan 3D mới |
| **Scans** | `/api/v1/scans/{id}/upload-images` | `POST` | Upload ảnh cho phiên scan |
| **Quality Runs** | `/api/v1/quality-runs/{run_id}/images/{image_id}` | `GET` | Xem báo cáo kiểm định kỹ thuật của ảnh |
| **Uploads** | `/api/v1/uploads/{id}/chunks/{index}` | `POST` | Tải lên từng chunk của file dung lượng lớn |
| **Model Ratings** | `/api/v1/scans/{scan_id}/ratings` | `POST` | Gửi/Cập nhật đánh giá (1-5 sao) mô hình 3D |

---

## 📋 Tiêu Chuẩn Phát Triển (Development Standards)

Tất cả thành viên trong nhóm phát triển Backend phải đọc và làm theo [BE_GUIDELINES.md](file:///c:/Users/KIETLT/Documents/GitHub/Memento3d/Memento3D-Backend/memeto3D/doc/BE_GUIDELINES.md) trước khi gửi PR:
1. Tuân thủ mô hình kiến trúc layer: `Controller → Service → Repository`.
2. Không viết SQL nối chuỗi (phải dùng Parameterized Query / ORM Binding).
3. Tuyệt đối không để xảy ra lỗi N+1 Query (dùng `JOIN FETCH`, `@EntityGraph` hoặc Batching).
4. Phân trang tất cả các API trả về danh sách, giới hạn `limit` tối đa.

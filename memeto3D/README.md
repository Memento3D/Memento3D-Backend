# 🧊 Memento3D - Backend Service

![Java 17](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4%2B%20%2F%204.1.1-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%7C%2017-blue.svg)
![JWT Auth](https://img.shields.io/badge/Security-JWT%20Blacklist-red.svg)
![Swagger/OpenAPI](https://img.shields.io/badge/OpenAPI-3.0-green.svg)
![Docker](https://img.shields.io/badge/Docker-Supported-blue.svg)

---

## 📌 Giới Thiệu (Overview)

**Memento3D Backend** là dịch vụ máy chủ trung tâm (Core Microservice/REST API) cho nền tảng tái tạo và dựng mô hình 3D từ ảnh/video (3D Photogrammetry / NeRF / Gaussian Splatting). 

Hệ thống quản lý toàn bộ quy trình từ xác thực người dùng, lưu trữ dữ liệu dung lượng lớn (Resumable Chunked Upload), kiểm tra chất lượng dữ liệu đầu vào (Quality Inspection), theo dõi tiến trình dựng hình 3D cho đến hỗ trợ các dịch vụ thương mại 3D.

👉 **Xem chi tiết tài liệu Cấu trúc & Kiến trúc hệ thống tại**: [STRUCTURE.md](../STRUCTURE.md)

---

## ⚡ Các Chức Năng Chính (Core Features)

1. **🔐 Xác thực & Quản lý Người Dùng (Auth & Users)**: Đăng ký, Đăng nhập JWT, Persistent Database Refresh Token Rotation, Google OAuth2/OIDC, Thu hồi Token qua Redis Blacklist, Profile cá nhân (`/api/v1/users/me`), Quản trị phân quyền Admin (`/api/v1/users`).
2. **📷 Quản lý Phiên Quét 3D (3D Scan Management)**: Khởi tạo & quản lý phiên quét 3D hỗ trợ 3 chế độ (`image`, `video`, `dual`), quản lý metadata ảnh scan, loại bỏ ảnh khỏi AI, xóa mềm video hoàn trả dung lượng lưu trữ (Storage Quota).
3. **🚀 Resumable Chunked Upload**: Hỗ trợ cắt file video/ảnh dung lượng lớn thành từng chunk để tải lên (`/api/v1/uploads/**`), có cơ chế Resume khi đứt kết nối mạng và xác thực tính toàn vẹn SHA-256.
4. **🔍 Kiểm tra Chất lượng Ảnh Scan (Quality Inspection)**: Phân tích tự động độ mờ/sắc nét (sharpness), độ sáng (brightness), ảnh trùng lặp (duplicates), điểm đặc trưng (feature points), nhiễu (SNR noise) và ghi đè kết quả (`/api/v1/quality-runs/**`).
5. **🛠️ 3D Reconstruction Pipeline**: Quản lý hàng đợi xử lý SfM/MVS, theo dõi tiến trình dựng 3D, hủy bỏ tác vụ đang chạy (`/api/v1/reconstruction-jobs/**`), và quản lý file đầu ra 3D (`.glb`, `.obj`, `.ply`).
6. **🛒 Đơn Hàng In 3D & Thanh Toán Trực Tuyến**: Đặt in mô hình 3D (`/api/v1/orders/**`), quản lý trạng thái in, tích hợp cổng thanh toán MoMo và ZaloPay với đối soát tự động và Webhook IPN (`/api/v1/webhooks/payments/**`).
7. **⭐ Đánh Giá Mô Hình (Model Rating)**: Đánh giá chất lượng mô hình 3D (1 - 5 sao, nhận xét) theo phiên quét (`/api/v1/scans/{scanId}/ratings`).

---

## 🌐 Chuẩn Hóa RESTful API (`/api/v1`)

Toàn bộ hệ thống Backend đã được chuẩn hóa về tiền tố `/api/v1` theo tiêu chuẩn RESTful API:
- **Authentication**: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/oauth2/google/url`.
- **Users**: `GET /api/v1/users/me`, `PATCH /api/v1/users/me`, `GET /api/v1/users`, `POST /api/v1/users`, `GET /api/v1/users/{userId}`, `PATCH /api/v1/users/{userId}`, `DELETE /api/v1/users/{userId}`.
- **Scans**: `POST /api/v1/scans`, `GET /api/v1/scans`, `GET /api/v1/scans/{scanId}`, `PATCH /api/v1/scans/{scanId}`, `DELETE /api/v1/scans/{scanId}`.
- **Scan Images**: `POST .../images`, `GET .../images`, `GET .../images/{imageId}`, `PATCH .../images/{imageId}`, `DELETE .../images/{imageId}`.
- **Scan Video**: `POST .../video`, `GET .../video`, `DELETE .../video`.
- **Quality Runs**: `GET .../quality-runs`, `POST .../quality-runs`, `GET /api/v1/quality-runs/{runId}`, `GET .../images`, `POST .../overrides`.
- **Reconstruction Jobs**: `POST .../reconstruction-jobs`, `GET .../reconstruction-jobs`, `GET /api/v1/reconstruction-jobs/{jobId}`, `GET .../outputs`, `POST .../cancellations`.
- **3D Models & Ratings**: `GET /api/v1/models/{modelId}`, `GET .../ratings`, `GET .../rating`, `POST .../ratings`, `PUT .../rating`, `DELETE .../rating`.
- **Chunked Uploads**: `POST /api/v1/uploads`, `GET /api/v1/uploads/{uploadId}`, `PUT .../chunks/{chunkIndex}`, `DELETE /api/v1/uploads/{uploadId}`.
- **Orders**: `POST /api/v1/orders`, `GET /api/v1/orders`, `GET /api/v1/orders/{orderId}`, `PATCH /api/v1/orders/{orderId}`, `DELETE /api/v1/orders/{orderId}`.
- **Payments & Webhooks**: `POST /api/v1/payments`, `GET /api/v1/payments`, `GET /api/v1/payments/{paymentId}`, `POST /api/v1/webhooks/payments/momo`, `POST /api/v1/webhooks/payments/zalopay`.

*(Các endpoint cũ `/api/...` được duy trì song song dưới dạng URL alias nhằm tương thích ngược tuyệt đối với Flutter Mobile Client và Payment Webhook callbacks).*

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

| Thành phần | Công nghệ |
| :--- | :--- |
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.4+ / 4.1.1 (Spring Web MVC, Spring Data JPA, Spring Security) |
| **Database** | PostgreSQL 16/17 (Docker/Production), H2 Database (Test) |
| **Security** | JWT (`io.jsonwebtoken 0.12.6`), BCrypt Encoder, Database Token Blacklisting |
| **Cloud Storage** | Cloudinary Java SDK 2.5.0 (CDN Storage) |
| **API Doc** | SpringDoc OpenAPI 3.0 / Swagger UI |
| **Build Tool** | Apache Maven 3.x (`mvnw`) |
| **Container** | Docker & Docker Compose |

---

## 💻 Hướng Dẫn Cài Đặt & Chạy (Quick Start)

### 1. Cấu hình môi trường (`.env`)
Tạo file `.env` tại thư mục `Memento3D-Backend/memeto3D/.env`:

```env
DB_HOST=localhost
DB_PORT=5431
DB_NAME=memento3d
DB_USERNAME=memento_user
DB_PASSWORD=memento_pass

JWT_SECRET=YourSuperSecretKeyWithAtLeast256BitsForSecurity123456
JWT_EXPIRATION=86400000

CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

---

### 2. Khởi chạy Database bằng Docker Compose (Bắt buộc trước khi chạy Source Code)

> ⚠️ **LƯU Ý QUAN TRỌNG**: Bạn **phải khởi chạy PostgreSQL Database container** qua Docker Compose để CSDL sẵn sàng trước khi chạy source code Spring Boot.

#### 🔹 Cách 1: Chỉ chạy Database PostgreSQL (Dùng khi chạy Dev Source Code qua Maven / IDE)
Mở terminal tại thư mục gốc của Backend và khởi chạy riêng container PostgreSQL:

```bash
cd Memento3D-Backend
docker-compose up -d
```
*Hoặc khởi chạy từ thư mục `memeto3D`:*
```bash
cd Memento3D-Backend/memeto3D
docker-compose up -d postgres
```

Kiểm tra trạng thái container PostgreSQL đảm bảo đã `healthy`:
```bash
docker ps
```

---

### 3. Chạy Source Code Backend

Sau khi container PostgreSQL đã hoạt động thành công, tiến hành khởi chạy ứng dụng Spring Boot:

#### 🔹 Khởi chạy trực tiếp bằng Maven (Local Dev)
```bash
cd Memento3D-Backend/memeto3D
./mvnw clean spring-boot:run
```
*(Trên Windows PowerShell: `.\mvnw.cmd clean spring-boot:run`)*

#### 🔹 Hoặc Khởi chạy Full-stack (Cả DB + Backend) bằng Docker Compose
Nếu muốn đóng gói và chạy toàn bộ ứng dụng trong Docker Container:
```bash
cd Memento3D-Backend/memeto3D
docker-compose up -d --build
```

---

## 📖 Swagger UI Document

Sau khi ứng dụng khởi chạy thành công:
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 📖 Tài Liệu Tham Khảo
- [Tài liệu Cấu trúc & Kiến trúc Chi tiết (STRUCTURE.md)](../STRUCTURE.md)
- [Quy chuẩn Phát triển Backend (BE_GUIDELINES.md)](doc/BE_GUIDELINES.md)

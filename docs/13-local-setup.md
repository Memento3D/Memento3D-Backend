# MEMENTO3D — LOCAL DEVELOPMENT SETUP GUIDE (WINDOWS POWERSHELL)

## 1. Prerequisites & System Requirements

Trước khi bắt đầu, đảm bảo máy tính phát triển của bạn đã cài đặt các công cụ sau:
- **Hệ điều hành**: Windows 10/11 (khuyến nghị chạy trên Windows Terminal / PowerShell 7+)
- **JDK**: OpenJDK 21 (Eclipse Temurin, Oracle JDK, hoặc Microsoft OpenJDK 21)
- **Node.js**: Node.js v18.x hoặc v20.x LTS kèm `npm` v9+
- **Maven**: Apache Maven 3.9+ (hoặc dùng sẵn `./mvnw` đính kèm trong repository)
- **Docker & Docker Desktop**: Chạy Redis container cục bộ
- **Git**: Git for Windows 2.40+

Kiểm tra phiên bản trong PowerShell:
```powershell
java -version
javac -version
node -v
npm -v
docker -v
```

---

## 2. Step-by-Step Installation

### Bước 1: Clone Repository
```powershell
cd C:\Users\KIETLT\Documents\GitHub
git clone https://github.com/KietLT/Memento3d.git
cd Memento3d
```

### Bước 2: Cấu hình Biến Môi Trường (.env)
Copy file mẫu `.env.example` thành file cấu hình:
```powershell
Copy-Item .env.example .env
```
Mở file `.env` và điền chuỗi kết nối Database Neon / Local Postgres, Redis, và các Secret key.

---

### Bước 3: Khởi động Redis Cục bộ (Local Redis)
Nếu chưa có Redis server cài trên máy, hãy khởi động nhanh qua Docker:
```powershell
docker run -d --name memento3d-redis -p 6379:6379 redis:7-alpine
```
Kiểm tra kết nối Redis:
```powershell
docker exec -it memento3d-redis redis-cli ping
# Kết quả mong đợi: PONG
```

---

### Bước 4: Khởi động Backend (Spring Boot 4.x)
Di chuyển vào thư mục backend và chạy ứng dụng:
```powershell
cd Memento3D-Backend\memeto3D

# Biên dịch & nạp thư viện
.\mvnw clean compile -DskipTests

# Chạy ứng dụng Spring Boot
.\mvnw spring-boot:run
```

> [!NOTE]
> Backend sẽ lắng nghe tại cổng `8080`:
> - API Base URL: `http://localhost:8080/api/v1`
> - Swagger UI: `http://localhost:8080/swagger-ui/index.html`
> - OpenAPI Docs: `http://localhost:8080/v3/api-docs`

---

### Bước 5: Khởi động Frontend Web (React Vite)
Mở một cửa sổ PowerShell mới và di chuyển vào thư mục frontend:
```powershell
cd C:\Users\KIETLT\Documents\GitHub\Memento3d\Memento3D-Fronted-Web

# Cài đặt toàn bộ npm dependencies
npm install

# Khởi động máy chủ phát triển Vite
npm run dev
```

> [!NOTE]
> Frontend sẽ lắng nghe tại:
> - URL: `http://localhost:5173` (hoặc `http://localhost:5174` nếu port 5173 bận)

---

## 3. Pre-seeded Test Accounts & Passwords

Hệ thống cung cấp sẵn các tài khoản demo đã được kích hoạt sẵn (`email_verified = true`, `status = ACTIVE`) với mật khẩu chung: **`123456`**.

| Phân hệ (Actor) | Role Code | Cổng đăng nhập | Email chính thức | Email phụ | Mật khẩu | Trang đích (Home) |
|---|---|---|---|---|---|---|
| **Khách hàng** | `ROLE_USER` | `/login` | `customer@memento3d.vn` | `user@memento3d.com` | `123456` | `/customer/home` |
| **Quản trị viên** | `ROLE_ADMIN` | `/admin/login` | `admin@memento3d.vn` | `admin@memento3d.com` | `123456` | `/admin/home` |
| **Quản lý Vận hành** | `ROLE_MANAGER` | `/manager/login` | `manager@memento3d.vn` | `manager@memento3d.com` | `123456` | `/manager/home` |
| **Thủ kho WMS** | `ROLE_WAREHOUSE` | `/warehouse/login` | `warehouse@memento3d.vn` | `warehouse@memento3d.com` | `123456` | `/warehouse/home` |
| **Kỹ thuật viên In** | `ROLE_OPERATOR` | `/operator/login` | `operator@memento3d.vn` | `operator@memento3d.com` | `123456` | `/operator/home` |
| **Đối tác Xưởng in** | `ROLE_PROVIDER` | `/provider/login` | `provider@memento3d.vn` | `provider@memento3d.com` | `123456` | `/provider/home` |
| **Tài xế Giao vận** | `ROLE_DRIVER` | `/driver/login` | `driver@memento3d.vn` | `driver@memento3d.com` | `123456` | `/driver/home` |

---

## 4. Verification & Smoke Test Checklist

1. Mở trình duyệt truy cập `http://localhost:5173/login`.
2. Đăng nhập với tài khoản `customer@memento3d.vn` / `123456`.
3. Kiểm tra chuyển hướng thành công đến `/customer/home` với danh sách danh mục sản phẩm từ CSDL.
4. Đăng xuất, truy cập `http://localhost:5173/manager/login` và đăng nhập với `manager@memento3d.vn` / `123456`.
5. Xác nhận hệ thống chuyển hướng vào `/manager/home` hiển thị các chỉ số vận hành và hàng đợi in 3D.
6. Truy cập `http://localhost:5173/warehouse/login` với `warehouse@memento3d.vn` / `123456` để kiểm tra Không gian làm việc Thủ kho WMS.
7. Mở `http://localhost:8080/swagger-ui/index.html`, bấm "Authorize", dán JWT token và gửi test thử request `GET /api/v1/auth/me`.

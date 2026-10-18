# MEMENTO3D — TROUBLESHOOTING & COMMON ISSUES GUIDE

## 1. Quick Troubleshooting Reference Table

| Triệu chứng / Thông báo lỗi | Nguyên nhân gốc rễ | Giải pháp khắc phục nhanh |
|---|---|---|
| `Port 8080 is already in use` | Tiến trình Java/Spring Boot cũ chưa tắt | Chạy script tìm và tắt PID đang chiếm dụng port |
| `Connection refused: localhost/127.0.0.1:6379` | Redis server chưa được khởi động | Khởi động Docker container Redis |
| `PSQLException: SSL connection has been closed` | Kết nối SSL tới Neon Postgres bị đứt/timeout | Kiểm tra cấu hình `sslmode=require` và Neon pooler URL |
| `Flexible constructor bodies are not supported` | Eclipse JDT/ECJ compiler can thiệp vào `target/classes` | Chạy lệnh `.\mvnw clean compile -DskipTests` bằng OpenJDK 21 |
| `CORS policy: No 'Access-Control-Allow-Origin'` | Frontend chạy trên port mới chưa được khai báo ở Backend | Cập nhật `allowedOrigins` trong `CorsConfig.java` |
| `Warehouse Access Denied` (Banner đỏ) | Tài khoản Operator/Manager chưa được gán kho cụ thể | Đã fix: `WarehouseSecurityService` tự động cho phép toàn quyền |
| `401 Unauthorized` ngay sau khi restart Backend | JWT secret trong `.env` chưa cố định hoặc Redis mất key | Thiết lập `JWT_SECRET` cố định trong file `.env` |

---

## 2. Chi tiết cách xử lý từng vấn đề

### 2.1 Cổng 8080 hoặc 5173 bị xung đột (Port In Use)
Trên Windows PowerShell:
```powershell
# 1. Tìm PID đang chiếm port 8080
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess

# 2. Tắt tiến trình bằng PID
Stop-Process -Id <PID> -Force

# Lệnh một dòng (One-liner):
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

---

### 2.2 Lỗi kết nối Redis (Redis Connection Refused)
```powershell
# Kiểm tra container Redis có đang chạy không
docker ps -a --filter "name=memento3d-redis"

# Nếu container đang stopped, khởi động lại:
docker start memento3d-redis

# Kiểm tra phản hồi PING từ Redis:
docker exec -it memento3d-redis redis-cli ping
# Kết quả mong đợi: PONG
```

---

### 2.3 Lỗi Neon PostgreSQL Timeout / SSL Connection
Khi sử dụng Neon Serverless Postgres, sau một thời gian không có request, máy chủ ảo của Neon sẽ ngủ đông (compute scale-to-zero). Khi có request đầu tiên, quá trình đánh thức có thể mất 2-3 giây:
- Đảm bảo tham số URL có chứa: `?sslmode=require&connectTimeout=30`.
- Sử dụng Neon Pooled Connection String (có chứa `-pooler` trong hostname) thay vì direct connection string.

---

### 2.4 Lỗi Eclipse JDT Compiler ("Flexible constructor bodies are not supported")
> [!WARNING]
> **Hiện tượng**: Khi chỉnh sửa file Java bằng VS Code hoặc Eclipse IDE có cài plugin Java Language Server (JDT-LS), plugin này có thể tự động build ngầm và đè bytecode bị lỗi vào thư mục `target/classes`.
> **Khắc phục**:
> 1. Tắt chế độ tự động build ngầm của extension.
> 2. Luôn dùng Apache Maven chính thức để build:
>    ```powershell
>    cd Memento3D-Backend\memeto3D
>    .\mvnw clean compile -DskipTests
>    ```

---

### 2.5 Lỗi Phân quyền Kho Hàng ("Warehouse Access Denied")
Nếu người dùng đăng nhập bằng vai trò `ROLE_OPERATOR`, `ROLE_ADMIN`, hoặc `ROLE_MANAGER` nhưng màn hình quản lý kho hiển thị cảnh báo không có quyền:
- Kiểm tra `WarehouseSecurityService.java`: Đã hỗ trợ cơ chế Full Access cho Quản trị viên/Kỹ thuật viên (`hasFullAccess()`) và tự động nạp toàn bộ các kho đang hoạt động (`ACTIVE`) nếu chưa có phân bổ kho riêng lẻ.

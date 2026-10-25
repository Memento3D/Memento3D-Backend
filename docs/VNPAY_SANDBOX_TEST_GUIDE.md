# Hướng Dẫn Kiểm Thử Tích Hợp VNPAY Sandbox — Memento3D

Tài liệu này cung cấp hướng dẫn chi tiết từng bước để kiểm thử luồng tích hợp cổng thanh toán **VNPAY Sandbox (v2.1.0)** trên hệ sinh thái Memento3D (Backend Spring Boot & Frontend React).

---

## 1. Cấu hình Môi trường

Thêm các biến môi trường sau vào file `.env` (hoặc cấu hình hệ thống / Docker environment) của Backend:

```env
# VNPAY Sandbox Credentials
VNPAY_TMN_CODE=YOUR_VNPAY_TMN_CODE
VNPAY_HASH_SECRET=YOUR_VNPAY_HASH_SECRET

# VNPAY Gateway URLs
VNPAY_PAYMENT_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:5173/payment/vnpay/return
VNPAY_IPN_URL=http://localhost:8080/api/v1/payments/vnpay/ipn
```

> **Lưu ý bảo mật:**
> - Tuyệt đối **không commit** file chứa secret key thực tế lên Git repository.
> - Sử dụng file `.env.example` làm mẫu thiết lập.
> - Trên môi trường Production, chỉ cần thay đổi biến môi trường trỏ đến URL và TMN Code / Hash Secret chính thức của VNPAY.

---

## 2. Thông tin Thẻ Test VNPAY Sandbox

Dưới đây là thông tin thẻ ngân hàng kiểm thử được VNPAY Sandbox hỗ trợ:

| Thông tin | Giá trị thẻ Test |
| :--- | :--- |
| **Ngân hàng** | **NCB** (Ngân hàng Quốc Dân) |
| **Số thẻ** | `9704198526191432198` |
| **Tên chủ thẻ** | `NGUYEN VAN A` |
| **Ngày phát hành** | `07/15` |
| **Mật khẩu OTP** | `123456` |

---

## 3. Khởi động Hệ thống

### 3.1 Khởi động Backend
```bash
cd Memento3D-Backend/memeto3D
./mvnw spring-boot:run
```
- Backend REST API chạy tại: `http://localhost:8080/api/v1`
- Swagger UI tài liệu API: `http://localhost:8080/swagger-ui.html`

### 3.2 Khởi động Frontend
```bash
cd Memento3D-Fronted-Web
npm run dev
```
- Frontend Web App chạy tại: `http://localhost:5173`

---

## 4. Luồng Kiểm thử Happy Path (Thanh toán Thành công)

1. **Đăng nhập Customer:**
   - Truy cập `http://localhost:5173/login`.
   - Đăng nhập với tài khoản khách hàng (`ROLE_USER`).
2. **Chọn Sản phẩm & Thêm vào Giỏ hàng:**
   - Truy cập Danh mục sản phẩm hoặc Trang chủ Memento3D.
   - Thêm sản phẩm in 3D kỷ vật vào giỏ hàng.
3. **Vào trang Thanh toán (Checkout):**
   - Bấm vào icon Giỏ hàng và chọn **"Tiến hành đặt hàng"** (`/checkout`).
   - Điền thông tin người nhận: Họ tên, Số điện thoại, Địa chỉ giao hàng.
4. **Chọn Phương thức Thanh toán:**
   - Trong mục *Phương thức thanh toán*, chọn **"Cổng VNPAY (QR / ATM / Thẻ quốc tế)"**.
5. **Tạo Đơn & Chuyển hướng VNPAY:**
   - Bấm nút **"Thanh toán với VNPAY"**.
   - Frontend gửi `POST /api/v1/orders` tạo đơn hàng trạng thái `PENDING_PAYMENT`.
   - Frontend gửi `POST /api/v1/payments/vnpay/create` với `{ "orderId": "<UUID>" }`.
   - Backend tính toán số tiền `amount = order.totalPrice * 100`, sinh `vnp_TxnRef` dạng `M3D-{orderCode}-{timestamp}`, sinh chữ ký số HMAC-SHA512 và trả về URL VNPAY.
   - Trình duyệt tự động chuyển hướng đến giao diện thanh toán VNPAY Sandbox.
6. **Thực hiện Thanh toán trên VNPAY Sandbox:**
   - Chọn phương thức: **"Thẻ nội địa và tài khoản ngân hàng"** -> Chọn ngân hàng **NCB**.
   - Nhập số thẻ: `9704198526191432198`.
   - Nhập tên chủ thẻ: `NGUYEN VAN A`.
   - Nhập ngày phát hành: `07/15`.
   - Bấm **"Tiếp tục"**.
   - VNPAY gửi mã xác thực OTP về màn hình mô phỏng, nhập OTP: `123456` và bấm **"Thanh toán"**.
7. **Xử lý Return URL & IPN:**
   - VNPAY redirect khách hàng về `http://localhost:5173/payment/vnpay/return?...`.
   - Frontend tự động gọi Backend `GET /api/v1/payments/vnpay/return?...` để xác minh chữ ký số và cập nhật kết quả.
   - Song song, VNPAY Server gọi webhook IPN `GET /api/v1/payments/vnpay/ipn` (hoặc POST).
   - Backend xác thực chữ ký, kiểm tra số tiền khớp 100%, cập nhật giao dịch sang `SUCCESS`.
   - Đơn hàng được cập nhật sang `CONFIRMED`, `paymentStatus = PAID`.
   - Kích hoạt quy trình tiếp theo: phát sinh Outbox event `PAYMENT_COMPLETED` để mở khóa entitlement và bắt đầu luồng chuẩn bị sản xuất / in 3D kỷ vật.
8. **Hiển thị Kết quả trên Giao diện:**
   - Màn hình thông báo **"✓ Thanh toán thành công"** với đầy đủ thông tin: Mã đơn hàng, Số tiền thanh toán (VNĐ), Mã giao dịch VNPAY, Ngân hàng thanh toán.
   - Bấm **"Xem đơn hàng"** để vào trang chi tiết đơn hàng (`/orders/:id`).
   - Kiểm tra bảng *Lịch sử giao dịch thanh toán*: Cổng VNPAY, Mã tham chiếu `M3D-...`, Trạng thái `Thành công`, Thời gian thanh toán.

---

## 5. Các Kịch bản Kiểm thử Ngoại lệ & Nâng cao

### Kịch bản 1: Khách hàng Hủy Giao dịch (Cancel Test)
1. Trong màn hình thanh toán VNPAY Sandbox, bấm nút **"Hủy giao dịch"** hoặc quay lại trang web.
2. VNPAY redirect về Return URL với `vnp_ResponseCode = 24`.
3. Backend map mã `24` -> `PaymentStatus.CANCELLED`.
4. Trang kết quả hiển thị thông báo: **"Thanh toán đã hủy"**.
5. Bấm nút **"Thanh toán lại"** -> Mở modal chọn thanh toán để thử lại mà không làm mất đơn hàng.

### Kịch bản 2: Giao dịch Thất bại (Failure Test - Sai OTP / Không đủ số dư)
1. Trên giao diện VNPAY Sandbox, nhập sai OTP hoặc nhập mã OTP giả định lỗi số dư.
2. VNPAY trả response code lỗi (ví dụ: `51` - Số dư không đủ, hoặc `11` - Hết hạn chờ thanh toán).
3. Backend lưu audit log chi tiết response code và gateway status, cập nhật giao dịch `FAILED`.
4. Trang kết quả hiển thị: **"Thanh toán chưa thành công"**. Đơn hàng vẫn ở trạng thái chưa thanh toán, cho phép khách hàng thanh toán lại.

### Kịch bản 3: Khách hàng Đóng Trình duyệt (Browser Closed Test)
1. Sau khi nhập OTP và thanh toán thành công trên VNPAY Sandbox, người dùng **đóng ngay trình duyệt**, không đợi redirect về Return URL.
2. VNPAY server-to-server IPN gửi webhook đến Backend `GET /api/v1/payments/vnpay/ipn`.
3. Backend xử lý độc lập: Xác thực chữ ký, cập nhật Payment sang `SUCCESS`, cập nhật Order sang `CONFIRMED`/`PAID`, kích hoạt Outbox event.
4. Khi khách hàng mở lại web và vào *Chi tiết đơn hàng*, đơn hàng đã ở trạng thái **Đã thanh toán**.

### Kịch bản 4: Xử lý Trùng lặp IPN (Idempotency Test)
1. Sử dụng Postman hoặc Curl gửi lại request IPN hợp lệ của một giao dịch đã `SUCCESS`:
   ```bash
   curl -X GET "http://localhost:8080/api/v1/payments/vnpay/ipn?vnp_Amount=...&vnp_TxnRef=...&vnp_SecureHash=..."
   ```
2. Backend kiểm tra thấy giao dịch đã ở trạng thái `SUCCESS`.
3. Backend trả về ngay JSON chuẩn VNPAY:
   ```json
   {
     "RspCode": "02",
     "Message": "Order already confirmed"
   }
   ```
4. Hệ thống **không** trừ tiền lần 2, **không** trigger duplicate event, bảo vệ tính toàn vẹn dữ liệu.

### Kịch bản 5: Chữ ký không Hợp lệ (Invalid Signature Test)
1. Giả lập request IPN hoặc Return với `vnp_SecureHash` bị thay đổi (tampered hash).
2. Backend dùng `MessageDigest.isEqual` kiểm tra chữ ký không khớp.
3. Transaction trong database được giữ nguyên trạng thái `PENDING`.
4. IPN trả về:
   ```json
   {
     "RspCode": "97",
     "Message": "Invalid Checksum"
   }
   ```

### Kịch bản 6: Sai lệch Số tiền (Amount Mismatch Test)
1. Giả lập request IPN với `vnp_Amount` bị sửa đổi khác với giá trị gốc của đơn hàng.
2. Backend phát hiện: `vnp_Amount / 100 != transaction.amount`.
3. Backend ghi nhận log cảnh báo an ninh, **không** cập nhật `SUCCESS`, và trả về:
   ```json
   {
     "RspCode": "04",
     "Message": "Invalid Amount"
   }
   ```

### Kịch bản 7: Thanh toán Lại Đơn hàng (Retry Payment Test)
1. Tạo thanh toán cho đơn hàng #ORD01, sau đó cố tình hủy hoặc để hết hạn (giao dịch 1: `CANCELLED` hoặc `EXPIRED`).
2. Khách hàng vào trang *Chi tiết đơn hàng*, bấm **"Thanh toán lại"**.
3. Hệ thống tạo giao dịch thanh toán thứ 2 với mã tham chiếu mới `M3D-ORD01-{timestamp2}`.
4. Thực hiện thanh toán thành công giao dịch 2.
5. Giao dịch 1 vẫn lưu lại trong lịch sử để đối soát; giao dịch 2 chuyển `SUCCESS`, đơn hàng chuyển `PAID`.

---

## 6. Kiểm tra Quản trị Admin

Admin có thể tra cứu và đối soát toàn bộ giao dịch thanh toán:
- **API Danh sách:** `GET /api/v1/admin/payments?status=SUCCESS&paymentMethod=VNPAY`
- **API Chi tiết:** `GET /api/v1/admin/payments/{id}`
- Hỗ trợ lọc theo: `status`, `paymentMethod`, `transactionReference`, `dateFrom`, `dateTo`.
- Sắp xếp mặc định: `createdAt DESC`.
- Sử dụng JPA Specification tối ưu, không phát sinh câu lệnh native SQL dư thừa và ngăn chặn lỗi N+1.

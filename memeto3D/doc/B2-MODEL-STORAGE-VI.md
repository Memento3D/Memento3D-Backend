# B2 cho GLB của Memento3D

Bucket private: `memento3d-models-tiendon`; region `us-east-005`.

## Điền cấu hình

Mở file `.env` trong **memeto3D** (cùng cấp `docker-compose.yml`, không phải frontend) và thêm/cập nhật mỗi tên biến đúng một lần:

```dotenv
MODEL_STORAGE_PROVIDER=b2
B2_ENDPOINT=https://s3.us-east-005.backblazeb2.com
B2_REGION=us-east-005
B2_BUCKET=memento3d-models-tiendon
B2_KEY_ID=thay_bang_keyID_cua_ban
B2_APPLICATION_KEY=thay_bang_applicationKey_cua_ban
B2_URL_TTL_SECONDS=3600
```

Không commit `.env`, không đặt key vào FE/app hoặc gửi vào chat. Dùng standard application key có Read and Write giới hạn bucket này. Không cần List All Buckets vì BE gọi trực tiếp bucket đã biết.

## Kiểm tra upload/download và cấu hình CORS

Trong PowerShell:

```powershell
cd D:\FPTU\07\SBA301\Memento3D-Backend\memeto3D
.\scripts\Test-B2Storage.ps1 -ConfigureCors
```

Script chỉ đọc biến B2 từ `.env`, chạy một test upload file tổng hợp 64 byte, tải qua signed URL và kiểm tra download không ký bị từ chối. Không chạy AI hoặc dùng ảnh người dùng. Một object kiểm tra nhỏ được giữ trong bucket. Key và signed URL không được in. Nếu SDK báo Access Denied khi cấu hình CORS, kiểm tra key có quyền cấu hình bucket; không đổi bucket thành public.

Mặc định cho phép GET/HEAD từ `http://localhost:5173` và `http://127.0.0.1:5173`. Khi có FE production:

```powershell
.\scripts\Test-B2Storage.ps1 -ConfigureCors -Origins 'http://localhost:5173','https://ten-fe-cua-ban.example'
```

Chỉ rule `memento3d-model-viewer` được thay; các CORS rule khác được giữ. Không cấp quyền browser ghi file; upload model đi từ BE. Chạy lại không có `-ConfigureCors` nếu chỉ muốn kiểm tra upload/download.

## Áp dụng BE

Giữ AI/ngrok đang chạy để BE tải được artifact của job hiện tại. Build và recreate riêng backend, không cần xóa database/volume:

```powershell
docker compose up -d --build --no-deps backend
docker logs --since 5m --follow memeto3d-backend
```

Job vẫn `running/uploading` sẽ được scheduler thử finalizer lại: tải GLB đã có từ AI, upload B2 và commit output. Không gọi nút retry AI chỉ để sửa storage. Nếu job đã failed/cancelled hoặc ngrok đổi URL, kiểm tra trạng thái/config trước.

Kiểm tra GET job chuyển `done`, GET outputs trả `delivery_url` HTTPS của B2 có `X-Amz-Signature`, rồi FE tải GLB được. URL hết hạn sau một giờ; gọi lại API outputs để lấy URL mới. Nếu download 200 nhưng browser báo CORS, bổ sung đúng origin FE bằng script trên.

## Phạm vi triển khai

- Cloudinary tiếp tục phục vụ ảnh/avatar và các model cũ.
- `MODEL_STORAGE_PROVIDER` mặc định vẫn `cloudinary` để giữ cấu hình cũ; đặt `b2` trong `.env` để chọn B2.
- Finalizer GLB dùng ModelStoragePort; B2 lưu object theo user/scan/job/attempt. Kiểm tra size và SHA-256 metadata; retry không upload lại object đã khớp, không ghi đè artifact khác checksum.
- DB lưu `storage_provider=b2`, object key và reference `b2://bucket/key`; không lưu signed URL sắp hết hạn làm reference chính. API outputs/preview và download digital asset tạo URL sau khi kiểm tra quyền hiện có.
- Không thay quota/tier/payment hoặc mở quyền tải high-quality.
- Đây mới là chuyển **GLB do AI output endpoint trả về** sang B2. AI hiện trả bản web; việc lưu master, file sản xuất đã duyệt và slicing vào B2 là phần tiếp theo, chưa tự triển khai trong bước này.
- Giới hạn download 50 MiB của BE hiện giữ nguyên; muốn tải master lớn hơn phải mở rộng contract có kiểm soát.

Rollback provider về `cloudinary` chỉ nên dùng khi không cần đọc output B2; muốn đọc B2 vẫn cần bật cấu hình B2 hợp lệ.

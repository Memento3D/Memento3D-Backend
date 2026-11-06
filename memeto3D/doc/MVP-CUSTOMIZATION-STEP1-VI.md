# Customization MVP — bước 1: catalog/version và kiểm tra B2

## Phạm vi

Thêm catalog asset có phiên bản, importer kiểm tra 15 object B2, API đọc catalog với URL tải mới mỗi lần. Không thay CustomizationOption hiện có. Chưa nối bước nhận fit của AI, draft người dùng, snapshot đơn hoặc luồng in.

Migration V27 tạo ba bảng: customization_catalogs, customization_asset_versions, customization_catalog_members. Migration chỉ tạo schema; không tự kích hoạt hoặc import catalog. Thay đổi schema phải được áp dụng bằng quy trình deploy có kiểm soát; agent chưa áp dụng vào Neon.

## Kiểm tra file B2 riêng, không ghi database

Từ thư mục memeto3D:

```powershell
.\scripts\Verify-MVPCatalogStorage.ps1
```

Script đọc các biến B2 được cho phép từ environment/.env, không in giá trị key. Chỉ GET và kiểm tra byte size + SHA-256 của 15 file theo package server. Không upload, sửa CORS hay gọi import. File upload bằng web console không có metadata SHA của app vẫn được kiểm tra đầy đủ từ stream, không dùng ETag thay cho SHA-256.

## Sau khi deploy schema lên môi trường phát triển

Chỉ ADMIN được import:

```http
POST /api/v1/admin/customization-catalogs/mvp-v1/import
Authorization: Bearer <admin-access-token>
```

Không có request body hoặc key B2 trong request. Manifest có sẵn tại src/main/resources/customization/mvp-v1-import.json gồm ba asset, thumbnail Cloudinary và 15 object pin. Toàn bộ network I/O kiểm tra B2 xảy ra trước write transaction. Khi đủ checksum mới khóa catalog, tạo/reuse version và kích hoạt atomically. Thiếu/sai file trả lỗi, không kích hoạt catalog.

Import lại cùng package không tạo thêm version/membership. Catalog đã ACTIVE hoặc asset version đã tồn tại với metadata khác bị từ chối 409; dùng phiên bản mới. DB unique constraints và khóa catalog bảo vệ hai request import đồng thời.

Người dùng đã đăng nhập đọc:

```http
GET /api/v1/customization-catalogs/mvp-v1
Authorization: Bearer <access-token>
```

Response có groups, defaultSelection, bodyPreset, assets (UUID phiên bản, code/version, thumbnail Cloudinary, GLB URL có thời hạn, checksum, compatibility và printReady=false). Không trả B2 application key. Catalog chưa import/ACTIVE không được công bố. Slot headwear loại trừ mũ/tóc; glasses cho phép không kính. Asset dùng chung vẫn cần FitResult theo từng job khi nối bước 2, không đặt asset thẳng vào scene bằng cách căn giữa độc lập.

## Flyway

Startup không còn tự repair checksum. Chỉ khi operator đã đối chiếu và chủ động bật app.database.flyway-repair-before-migrate=true mới repair. Không dùng tùy chọn này để bỏ qua sai lệch schema. Migration mới không sửa nội dung V1–V26.

## Kiểm thử

Controller kiểm tra 401/403/admin import và JSON response. Service kiểm tra network trước transaction, chặn import khi B2 lỗi, signed URL và trạng thái catalog. Validator kiểm tra checksum/path/metadata/compatibility. Adapter kiểm tra stream ngắn/dài/sai hash mà không ghi object.

CatalogPostgresIntegrationTest chỉ chạy khi CATALOG_TEST_JDBC trỏ đúng jdbc:postgresql://127.0.0.1:<port>/memento_catalog_test. Dùng database Docker riêng để kiểm tra V27, Hibernate validate, JSONB, unique constraints, rollback, import đồng thời và đọc catalog chỉ hai query. Không trỏ test này vào Neon.

Bước kế tiếp: mở rộng AI DTO/client/finalizer để nhận output_kind, customization_ready, manifest và file fit theo job/attempt; sau đó mới thêm draft JSON/revision và API scene cho FE.

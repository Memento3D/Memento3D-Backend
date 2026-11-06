# API custom MVP cho FE

Đã áp dụng V27–V30 trên Neon, import catalog `mvp-v1` và khởi động BE local bản mới. Luồng đặt sản xuất custom đã nối; xem [CUSTOM-PRINT-CHECKOUT-VI.md](CUSTOM-PRINT-CHECKOUT-VI.md). Output cũ không có fit không tự chuyển thành output custom: cần tạo job mới bằng AI customization-base. Kiểm chứng browser hiện dùng API có kiểm soát và GLB thật, chưa thay thế một lượt end-to-end bằng tài khoản thật qua AI/ngrok và B2.

## Điểm vào

Tất cả API yêu cầu Bearer token. `{modelOutputId}` là ID output GLB, không phải job ID, scan ID hoặc model-version ID. Dùng output ID từ API model hiện có.

| Method | Đường dẫn | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/models/{modelOutputId}/scene` | Base GLB, các asset đã fit, thumbnail, nhóm lựa chọn và draft hiện tại |
| GET | `/api/v1/models/{modelOutputId}/customization` | Đọc lựa chọn, không tải lại scene |
| PUT | `/api/v1/models/{modelOutputId}/customization` | Lưu lựa chọn JSON, không chạy AI/export GLB |

Các response này dùng camelCase, có `Cache-Control: no-store`. Scene gồm `fitResultId`, `baseModel`, `groups`, `assets`, `defaultSelection`, `customization`, `urlTtlSeconds`, `urlsExpireAt`. Asset có UUID `assetVersionId`, `slot`, `thumbnailUrl`, `url`, `sha256`, `matrixColumnMajor`. Không dùng code như `graduation-cap` thay cho UUID khi lưu.

## Render và lưu

1. GET scene. Load base GLB một lần, dựng danh sách thumbnail theo `groups[].assetVersionIds` và `assets`.
2. Dùng `customization.selection` làm lựa chọn ban đầu, kể cả chưa lưu (`exists=false`, `revision=0`, selection là mặc định).
3. Tải asset khi cần, cache theo `assetVersionId + sha256`. Gắn base và asset dưới cùng một root. Áp `matrixColumnMajor` đúng một lần bằng Three.js Matrix4.fromArray. Asset `fitted_geometry` đã ở tọa độ base, không fit/scale thêm. Scene dùng glTF Y-up.
4. `headwear` chọn một giữa mũ/tóc; kính có thể null. Thay lựa chọn trên FE ngay, chưa gọi AI hay PUT cho từng click.
5. Nút Lưu gửi đủ mọi slot, UUID asset từ chính scene đó:

```json
{
  "fitResultId": "<scene.fitResultId>",
  "revision": 0,
  "selection": {
    "headwear": "<assetVersionId cua mu hoac toc>",
    "glasses": null
  }
}
```

Response đầu tiên `exists=true`, `revision=1`. Sau lưu, dùng revision trả về cho lần tiếp theo. Một draft duy nhất được cập nhật, không phát sinh GLB mới. Retry cùng lựa chọn đã lưu không tăng revision; request thay đổi bằng revision cũ trả 409. FE giữ thay đổi đang chỉnh và cho người dùng tải bản mới trước khi quyết định ghi lại.

6. Khi mở lại, GET scene trả đúng output/attempt, fit và lựa chọn đã lưu. Job retry không thay fit của output cũ. Khi URL hết hạn, GET scene để lấy URL mới; giữ camera và lựa chọn chưa lưu trên FE, không tự reset chúng. Không lưu signed URL vào database/local design JSON.

## Quyền và lỗi

- Chỉ chủ model được lưu; nhân sự có quyền xem scan chỉ xem scene (`editable=false`).
- 400 `INVALID_CUSTOMIZATION_SELECTION`: thiếu slot, sai asset hoặc chọn null cho nhóm bắt buộc.
- 409 `CUSTOMIZATION_REVISION_CONFLICT`: fit/revision không khớp; đọc lại draft/scene.
- 409 `CUSTOMIZATION_NOT_AVAILABLE`: output không có fit custom; không hiển thị bộ chọn cho output legacy.
- 409 `CATALOG_ARCHIVED`: chỉ xem lại, không đổi bản custom. Scene trả `editable=false`.
- 409 `CUSTOMIZATION_DATA_INVALID`: dữ liệu đã lưu không khớp base/fit, cần kiểm tra BE.
- Scan đã xóa hoặc không có quyền xem: 404. Chưa đăng nhập: 401.

## Ranh giới MVP

`readyForMachine=false`: scene để preview, không phải chứng nhận in được. Lưu custom không khóa đơn hàng. Khi phát triển checkout, cần snapshot bất biến theo từng order item rồi export/kiểm tra file in từ snapshot đó. Chưa có API checkout snapshot/export trong bước này.

Kiểm tra triển khai tiếp theo: migrate database, import catalog đã verify, chạy một job mới, GET scene, thay mũ/tóc/kính và PUT, mở lại, thử revision cũ. Không cần tạo thêm model cho mỗi lựa chọn.

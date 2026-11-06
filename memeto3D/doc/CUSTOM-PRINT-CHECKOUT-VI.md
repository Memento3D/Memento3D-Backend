# API đặt sản xuất custom

V30 đã áp dụng trên Neon. Dữ liệu lưu ở `order_custom_designs`; snapshot của đơn không đổi theo draft cá nhân. Có trigger DB bảo vệ snapshot/output/item/order/chiều cao. `scripts/Apply-CustomPrintMigration.ps1 -Apply` là công cụ áp dụng riêng V30, không repair/reset Flyway.

## Tạo yêu cầu

`POST /api/v1/orders` (Bearer của chủ model):

```json
{
  "modelOutputId": "UUID output GLB",
  "fitResultId": "UUID từ scene",
  "customizationRevision": 3,
  "requestedHeightCm": 12,
  "recipientName": "Người nhận",
  "phone": "0901234567",
  "shippingAddress": "Địa chỉ nhận hàng",
  "note": "Ghi chú",
  "idempotencyKey": "custom:UUID-cua-lan-checkout"
}
```

Không gửi `totalPrice` hoặc `quoteId` cho nhánh này. User phải lưu trước; fit/revision phải khớp chính xác. BE ưu tiên custom trước giỏ, không xóa giỏ. Cùng key trả cùng đơn; khác payload với key đã dùng bị 409. Key bắt buộc, client phải giữ payload/key khi không biết kết quả POST.

201 trả OrderResponse ở `PENDING`, `PENDING_REVIEW`, tổng tạm thời 0 vì chưa có báo giá; đây không phải đơn miễn phí. Đơn có một item custom Resin SLA, số lượng 1. Xưởng duyệt/báo giá sau; giá không do client quyết định.

## Bản cố định và file

`GET /api/v1/orders/{orderId}/custom-design` dành cho chủ đơn hoặc ADMIN/MANAGER/OPERATOR. Response gồm `id`, `orderItemId`, output/fit/revision, selection, asset names, `requestedHeightCm`, `snapshotSha256`, trạng thái, `downloadUrl`, `artifactSha256`, `artifactSizeBytes`, `modelVersionId`, `approvedForProduction` và ghi chú duyệt. `approvedForProduction=true` sau duyệt kỹ thuật để mở luồng chuẩn bị sản xuất; `readyForMachine=false` vì GLB chưa phải file slice chạy máy.

Trạng thái: QUEUED → EXPORTING → READY → APPROVED hoặc REJECTED. Lỗi xuất là FAILED; `POST .../custom-design/retry` xếp lại chính snapshot đó. Lease export quá 20 phút được thu hồi khi worker hoạt động lại.

Worker tải file theo storage reference và kiểm tra hash/size, ghép GLB static không đổi vertex/index/texture, áp ma trận đúng một lần; scale đồng đều toàn mẫu đến chiều cao yêu cầu (glTF mét). Định dạng graph/extension không hỗ trợ bị từ chối thay vì làm mất chi tiết. B2 lưu `customization/order-{designId}.glb`; URL ký được cấp mới qua GET, không lưu vào snapshot.

Worker dùng `customPrintExportScheduler`; các scheduled task khác dùng `taskScheduler`, tránh export chặn AI status sync.

## Duyệt, báo giá, thanh toán, Print Job

`POST .../custom-design/review` chỉ ADMIN/MANAGER/OPERATOR:

```json
{"approved":true,"manufacturingChecked":true,"notes":"Đã kiểm tra hình học, độ khít, đơn vị và kích thước in."}
```

File phải ở READY/REJECTED để review. APPROVED là xác nhận kỹ thuật của xưởng, không phải kết luận tự động của AI. Nhánh model review cũ không được approve/request-revision bản đã thuộc đơn.

Sau duyệt file, dùng API feasibility hiện có với giá dương → khách POST `/orders/{id}/accept-quote` → thanh toán. BE chặn thanh toán custom nếu chưa duyệt file/chấp thuận báo giá. Không cho thay `revisionGlbUrl` để tráo file snapshot; cần sửa thiết kế cá nhân và tạo yêu cầu mới nếu thiết kế thay đổi.

`POST /api/v1/print-jobs` phải gửi `orderId`, `orderItemId` của snapshot và material/ước tính thực tế. `modelVersionId` nếu có phải đúng bản trong response; nếu bỏ, BE chọn đúng bản của đơn. Cần file APPROVED, đơn ACCEPTED, đã PAID/SUCCESS và PROCESSING. Không lấy latest version của scan cho đơn custom.

Nhánh resin sử dụng đúng file frozen và không tạo URL STL bằng cách thay đuôi GLB. Chuẩn bị STL/support/slicing là bước xưởng; file slice thật cần SHA-256. Không tự coi scene ghép là mesh kín hoặc GOO/CTB sẵn sàng chạy máy.

## Kiểm chứng

Đã build BE/Docker, áp V30, kiểm tra Swagger/runtime healthy. Kiểm tra có trọng tâm cho revision/snapshot/checkout/payment gating và ghép GLB local thật ở `target/custom-print-validation/`. Web đã chạy Chrome với GLB ghép thật và API mô phỏng để kiểm tra UI/checkout/retry/review/quote/accept. Chưa tạo đơn thật/upload artifact đơn lên B2/thanh toán sandbox trong lượt kiểm chứng này.

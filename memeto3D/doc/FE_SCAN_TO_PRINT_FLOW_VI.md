# Luồng React FE: Scan, dựng model, đặt hàng và in

Tài liệu này mô tả các API và hành vi đang có trong BE tại thời điểm kiểm tra. Đây là hướng dẫn để FE gọi đúng các endpoint hiện hữu; các bước được ghi rõ là “chưa tự động” hoặc “cần xác nhận” không nên được FE giả định là backend đã xử lý.

## 1. Sơ đồ tổng quát

```text
React FE
  ├─ tạo scan, upload ảnh + view_hint ───────────────> BE /api/v1/scans
  ├─ yêu cầu dựng preview / model chất lượng cao ───> BE /api/v1/...reconstructions...
  ├─ xem trạng thái và output ───────────────────────> BE /api/v1/reconstruction-jobs, /api/v1/models
  ├─ xem giá, tạo order, thanh toán ─────────────────> BE /api/v1/pricing, /api/v1/orders, /api/v1/payments
  └─ xem danh sách order của mình ───────────────────> BE /api/v1/orders/me

BE
  ├─ lưu ảnh và metadata vào DB/storage
  ├─ gửi bộ ảnh front/left/right (+ back) sang Head API
  ├─ lưu trạng thái job và output
  └─ điều phối order, payment và các API nội bộ cho staff sản xuất

Staff (operator/manager/admin)
  └─ duyệt feasibility/model, chuẩn bị file in, slicing, bàn giao, QC
```

FE chỉ gọi BE. Không gửi AI API key hoặc gọi các route nội bộ của Head API trực tiếp từ browser.

## 2. Điều kiện chung cho request từ FE

- Các endpoint cần đăng nhập yêu cầu:

  ```http
  Authorization: Bearer <JWT>
  ```

- Mỗi API trong tài liệu dùng prefix `http://localhost:8080` khi chạy BE local.
- Dùng Swagger BE tại `http://localhost:8080/swagger-ui/index.html` để xem request schema thực tế.
- FE lấy ID từ response của từng bước; không tự đoán ID.

## 3. Tạo scan và tải ảnh

### 3.1 Tạo scan session

```http
POST /api/v1/scans
Content-Type: application/json
```

```json
{
  "name": "head-scan-01",
  "input_mode": "image"
}
```

Lưu `id` từ response làm `scanId`. Scan mới bắt đầu ở trạng thái `draft`.

### 3.2 Upload ảnh cùng nhãn góc

Upload từng ảnh bằng một request:

```http
POST /api/v1/scans/{scanId}/images?view_hint=front
Content-Type: multipart/form-data
```

Multipart field:

| Field | Giá trị |
|---|---|
| `file` | File ảnh |
| `view_hint` | `front`, `left`, `right` hoặc `back` |

Lặp lại cho ảnh trước, trái, phải; `back` là tùy chọn. API lưu nhãn trên metadata của ảnh. Backend không tự nhận diện nội dung ảnh để xác thực nhãn, nên FE/người dùng phải chọn đúng ảnh.

### 3.3 Kiểm tra ảnh đã upload

```http
GET /api/v1/scans/{scanId}/images
```

Đọc `images[].id`, `images[].view_hint`, `images[].upload_status` trong response. Cần có đúng một ảnh `front`, `left`, `right`; một nhãn không được gán cho nhiều ảnh active trong cùng scan.

Nếu ảnh cũ chưa có nhãn, có thể sửa bằng:

```http
PATCH /api/v1/scans/{scanId}/images/{imageId}
Content-Type: application/json
```

```json
{
  "view_hint": "front"
}
```

PATCH chỉ là đường sửa metadata; ảnh mới nên gửi `view_hint` ngay khi upload.

## 4. Dựng model từ bộ ảnh

Trước khi gửi job, FE có thể gọi:

```http
GET /api/v1/reconstruction/capabilities
```

Capabilities hiện công bố 3–4 ảnh, JPEG/PNG và tối đa 12 MiB mỗi ảnh.

### 4.1 Free preview

Kiểm tra quota:

```http
GET /api/v1/reconstructions/quotas/me
```

Tạo preview:

```http
POST /api/v1/reconstructions/previews
Content-Type: application/json
```

```json
{
  "scanId": "<scanId>",
  "inputImageId": "<imageId>",
  "profile": "preview_fast"
}
```

Lưu ý: preview request hiện vẫn yêu cầu `scanId` và `inputImageId` theo `CreatePreviewJobRequest`. Dùng ID của ảnh `front`. Dù endpoint preview nhận một ID chính, service tạo snapshot và gửi các ảnh đã gắn nhãn sang AI.

Response `202 Accepted` trả `jobId`. Theo dõi bằng:

```http
GET /api/v1/reconstructions/{jobId}
```

Khi job đã hoàn tất, xem thông tin preview:

```http
GET /api/v1/reconstructions/{jobId}/preview
```

Endpoint preview trả thông tin xem trước; nó không phải quyền tải model chất lượng cao. Quota mặc định được tạo với 3 lượt khi user chưa có quota.

### 4.2 Tạo reconstruction job tiêu chuẩn

Endpoint tiêu chuẩn hiện cho phép bỏ `input_image_id`; backend tự dùng ảnh `front` làm ảnh chính và vẫn gửi cả bộ ảnh đã gắn nhãn:

```http
POST /api/v1/scans/{scanId}/reconstruction-jobs
Content-Type: application/json
```

```json
{
  "profile": "default"
}
```

`input_image_id` cũ vẫn được chấp nhận để tương thích client cũ, nhưng không cần gửi. Đây là khác biệt so với API free preview ở mục 4.1.

Backend chỉ dispatch job tới AI khi `AI_DISPATCH_ENABLED=true` và cấu hình URL/API key BE–AI hợp lệ. FE không bật cấu hình này; đây là cấu hình triển khai BE.

### 4.3 Theo dõi và lấy output

Theo dõi job:

```http
GET /api/v1/reconstructions/{jobId}
```

Hoặc lấy job mới nhất của scan:

```http
GET /api/v1/scans/{scanId}/reconstruction-jobs
```

Khi `status` là `done`, lấy artifact:

```http
GET /api/v1/reconstruction-jobs/{jobId}/outputs
GET /api/v1/scans/{scanId}/outputs
```

Response output có các trường như `format`, `delivery_url`, `file_size_bytes`, `sha256`. FE có thể dùng URL để hiển thị/tải model theo quyền truy cập của tài khoản.

## 5. Giá và đặt hàng

Hệ thống hiện có **hai cách báo giá khác nhau**. FE không nên coi chúng là cùng một API.

### 5.1 Pricing engine cho sản phẩm trong catalog

Lấy sản phẩm:

```http
GET /api/v1/products
GET /api/v1/products/{productId}
```

Tạo báo giá server-side:

```http
POST /api/v1/pricing/quotes
Content-Type: application/json
```

```json
{
  "productId": "<productId>",
  "materialId": "<materialId>",
  "sizeCm": 15,
  "customization": {},
  "shippingRequired": true,
  "shippingAddress": "..."
}
```

Lưu `id` của quote và hiển thị `totalAmount` cùng `lineItems`. Có thể truy vấn lại bằng:

```http
GET /api/v1/pricing/quotes/{quoteId}
```

Giá do BE tính; FE không tự tính giá cuối hoặc sửa `totalPrice` để thay thế quote.

### 5.2 Giỏ hàng và checkout sản phẩm

```http
GET    /api/v1/cart
POST   /api/v1/cart/items
PATCH  /api/v1/cart/items/{cartItemId}
DELETE /api/v1/cart/items/{cartItemId}
```

Tạo order:

```http
POST /api/v1/orders
Content-Type: application/json
```

Body cần thông tin người nhận, địa chỉ, điện thoại và có thể gồm `quoteId`, `fulfillmentType`, `paymentType`, `idempotencyKey`. Backend checkout giỏ hàng và tính tổng từ sản phẩm trong giỏ.

### 5.3 Order cho scan/custom portrait

`POST /api/v1/orders` cũng có `scanId` và `quoteId`. Tuy nhiên code hiện tại chọn nhánh checkout giỏ hàng trước; nếu user đang có cart item, order sẽ được tạo theo giỏ hàng thay vì nhánh bespoke scan. FE không nên gửi scan order khi cart còn sản phẩm với giả định cả hai sẽ tự gộp.

Nhánh scan order hiện xác thực quote qua Pricing Engine nếu có `quoteId`. Không dùng `totalPrice` do FE tự gửi làm nguồn giá; code còn có fallback legacy cho phép amount từ request nếu >= 50.000 VND, nhưng đây không phải luồng định giá an toàn nên FE không nên dựa vào.

### 5.4 Báo giá thủ công cho đơn custom — luồng tách biệt

Khách gửi yêu cầu báo giá thủ công:

```http
POST /api/v1/quotations/manual
```

```json
{
  "scanId": "<scanId>",
  "productId": "<optional-productId>",
  "specifications": "Mô tả kích thước/vật liệu/hoàn thiện",
  "customerNotes": "Ghi chú"
}
```

Khách xem danh sách:

```http
GET /api/v1/quotations/manual/me
GET /api/v1/quotations/manual/{quotationId}
```

Manager xem yêu cầu, gửi giá; khách chấp nhận hoặc từ chối:

```http
GET  /api/v1/quotations/manual/admin/pending             # Manager/Admin
PATCH /api/v1/quotations/manual/admin/{id}/quote         # Manager/Admin
POST /api/v1/quotations/manual/{id}/accept               # Customer
POST /api/v1/quotations/manual/{id}/reject               # Customer
```

**Giới hạn cần biết:** manual quotation hiện lưu trạng thái và giá báo thủ công, nhưng accept quotation không tự tạo order/payment và `CreateOrderRequest` không nhận `manualQuotationId`. Vì vậy luồng báo giá thủ công chưa được nối end-to-end tới checkout. Cần thống nhất/triển khai quan hệ quotation → order ở BE trước khi FE có thể dùng manual quotation làm giá checkout tự động.

## 6. Thanh toán order

Xem order:

```http
GET /api/v1/orders/{orderId}
GET /api/v1/orders/me
```

Với VNPAY:

```http
POST /api/v1/payments/vnpay/create
Content-Type: application/json
```

```json
{
  "orderId": "<orderId>",
  "returnUrl": "http://localhost:5173/payment/vnpay/return",
  "orderInfo": "Thanh toan don hang"
}
```

FE chuyển browser tới `paymentUrl` trong response. `GET /api/v1/payments/vnpay/return` là callback người dùng quay về; server-to-server IPN tại `/api/v1/payments/vnpay/ipn` mới là nơi gateway gửi kết quả cập nhật. Sau redirect, FE nên gọi lại `GET /api/v1/orders/{orderId}` hoặc kiểm tra payment status; không coi query params redirect của browser là bằng chứng thanh toán cuối cùng.

Một số provider khác dùng:

```http
POST /api/v1/orders/{orderId}/payments
```

với `paymentMethod` (MOMO/ZALOPAY, hoặc CASH theo xử lý hiện có). Xem [PAYMENT_GATEWAY_GUIDE_AND_TESTING.md](./PAYMENT_GATEWAY_GUIDE_AND_TESTING.md) để biết cấu hình gateway.

Đơn scan bespoke có thể cần workshop feasibility trước khi đủ điều kiện thanh toán. Manager/operator duyệt qua:

```http
POST /api/v1/manager/orders/{orderId}/feasibility-review
```

Customer chấp nhận feasibility/báo giá của order qua:

```http
POST /api/v1/orders/{orderId}/accept-quote
```

Đây là **workflow trên Order**, khác với `/api/v1/quotations/manual/{id}/accept`.

## 7. Reconstruction chất lượng cao sau khi thanh toán

Thanh toán order không tự động gọi AI reconstruction. Sau khi order đã `PAID`/`SUCCESS`, FE gọi:

```http
POST /api/v1/orders/{orderId}/reconstructions
```

Backend xác nhận order thuộc user, đã thanh toán và có `scanId`; sau đó tạo job high-quality với các ảnh `front`, `left`, `right` đã gắn nhãn. Theo dõi bằng `GET /api/v1/reconstructions/{jobId}` và lấy output bằng `GET /api/v1/reconstruction-jobs/{jobId}/outputs` hoặc `/api/v1/scans/{scanId}/outputs`.

Nếu API trả `PAYMENT_REQUIRED`, `NO_SCAN_ASSOCIATED` hoặc thiếu góc ảnh, dừng flow và xử lý lỗi tương ứng thay vì tự tạo job thay thế.

## 8. Duyệt model và luồng sản xuất in

### 8.1 Duyệt model

Xem version:

```http
GET /api/v1/models/{scanId}/versions
```

Khách duyệt version để cho phép sản xuất vật lý:

```http
POST /api/v1/models/versions/{versionId}/approve
```

Hoặc yêu cầu sửa:

```http
POST /api/v1/models/versions/{versionId}/request-revision
```

PrintJob cho order scan sẽ từ chối nếu model version chưa `APPROVED`.

### 8.2 Resin production (staff only)

Các endpoint dưới `/api/v1/production/resin` dành cho operator/manager/admin, không gọi từ màn hình customer checkout.

1. Chuẩn bị sản xuất theo order item:

   ```http
   POST /api/v1/production/resin/jobs/{orderItemId}/prepare
   ```

   Gửi `dimXMm`, `dimYMm`, `dimZMm`; các chiều bị giới hạn theo build volume ở service. Response trả `printJobId`.

2. Ghi nhận slicing:

   ```http
   POST /api/v1/production/resin/jobs/{printJobId}/slicing
   ```

   Lưu file slice `.goo`/`.ctb`, slicer, resin, màu và thời gian dự kiến. Thành công chuyển job tới `READY_FOR_PRINTING`.

3. Ghi nhận bàn giao file cho máy:

   ```http
   POST /api/v1/production/resin/jobs/{printJobId}/handoff
   ```

4. Xác nhận đã bắt đầu in thật:

   ```http
   POST /api/v1/production/resin/jobs/{printJobId}/start-printing
   ```

5. Cập nhật các bước wash/support removal/UV cure/finish:

   ```http
   POST /api/v1/production/resin/jobs/{printJobId}/post-processing
   ```

6. Ghi nhận QC:

   ```http
   POST /api/v1/production/resin/jobs/{printJobId}/qc
   ```

   QC `PASSED` chuyển tới `READY_FOR_DELIVERY`; `REJECTED` chuyển `FAILED`. Có thể tạo lần in lại cho job lỗi bằng:

   ```http
   POST /api/v1/production/resin/jobs/{printJobId}/reprint
   ```

7. Xem job:

   ```http
   GET /api/v1/production/resin/jobs/{printJobId}
   ```

### 8.3 PrintJob kho/xưởng tổng quát

BE cũng có API riêng `/api/v1/print-jobs` cho staff để tạo job, giữ vật liệu kho, cấp vật liệu, bắt đầu in và đối soát tiêu hao:

```http
POST   /api/v1/print-jobs
POST   /api/v1/orders/{orderId}/print-jobs
GET    /api/v1/print-jobs
GET    /api/v1/print-jobs/{id}
POST   /api/v1/print-jobs/{id}/reservations
POST   /api/v1/print-jobs/{id}/material-issues
POST   /api/v1/print-jobs/{id}/consumption-reconciliations
POST   /api/v1/print-jobs/{id}/start
```

Đây là flow vận hành staff, song song với Resin Production API; không phải một API FE customer duy nhất để “bấm đặt in”.

## 9. Các điểm chưa được backend nối tự động

1. **Manual quotation → Order:** chấp nhận manual quotation không tạo order; chưa có `manualQuotationId` trong create order.
2. **Scan order khi cart còn item:** `OrderServiceImpl` ưu tiên checkout cart trước nhánh scan. FE cần backend có contract rõ ràng trước khi hỗ trợ checkout scan + cart đồng thời.
3. **Order đã thanh toán → high-quality reconstruction:** FE phải gọi explicit `POST /api/v1/orders/{orderId}/reconstructions`; không tự chạy sau payment callback.
4. **Model output → order item → sản xuất:** resin preparation tra GLB DigitalAsset theo `orderItemId`, trong khi reconstruction output được truy xuất theo job/scan. Cần xác nhận backend đã liên kết artifact phù hợp tới order item trước khi vận hành luồng này.
5. **GLB → STL:** ResinProductionService hiện gọi conversion port với byte array rỗng (ghi chú xử lý async/cloud asset) và tạo `stlDeliveryUrl` bằng cách thay `.glb` thành `.stl`. Đây chưa phải bằng chứng rằng file STL thực đã được tạo/kiểm định; không hiển thị như một conversion hoàn tất nếu artifact chưa tồn tại.
6. **Pricing cho scan custom:** Pricing Engine quote yêu cầu `productId`; manual quotation là workflow khác và hiện chưa nối tới order. Không cho FE tự quyết định giá cuối.

Các điểm trên là ranh giới giữa API đang tồn tại và phần cần xác nhận/thiết kế tiếp trước khi hoàn thiện UX checkout custom scan.

## 10. Checklist tích hợp React

- [ ] Lưu `scanId` sau khi tạo scan.
- [ ] Upload ảnh riêng lẻ cùng `view_hint`; lưu `image.id`.
- [ ] Đọc lại gallery và xác nhận đủ nhãn trước khi bật nút tạo job.
- [ ] Phân biệt body preview với standard reconstruction; preview hiện bắt buộc `inputImageId`.
- [ ] Poll job BE theo `jobId`; dừng polling ở trạng thái terminal và hiển thị `error_code`/`error_message`.
- [ ] Khi job `done`, lấy output từ BE; xử lý link/403/expiry theo response, không truy cập storage bằng secret.
- [ ] Dùng quote do BE trả; không tin tổng giá do browser gửi.
- [ ] Sau payment redirect, query lại BE để xác nhận trạng thái.
- [ ] Chỉ hiển thị action sản xuất sau khi order, feasibility và model approval thỏa điều kiện.
- [ ] Không gọi API staff/operator từ token customer.

## Nguồn code chính

- [ScanController.java](../src/main/java/com/memento3d/scans/api/ScanController.java)
- [ReconstructionJobController.java](../src/main/java/com/memento3d/reconstruction/api/ReconstructionJobController.java)
- [ReconstructionJobServiceImpl.java](../src/main/java/com/memento3d/reconstruction/application/service/impl/ReconstructionJobServiceImpl.java)
- [OrderController.java](../src/main/java/com/memento3d/orders/api/OrderController.java)
- [OrderServiceImpl.java](../src/main/java/com/memento3d/orders/application/service/impl/OrderServiceImpl.java)
- [PricingController.java](../src/main/java/com/memento3d/pricing/api/PricingController.java)
- [ManualQuotationController.java](../src/main/java/com/memento3d/quotation/api/ManualQuotationController.java)
- [VnpayPaymentController.java](../src/main/java/com/memento3d/payment/api/VnpayPaymentController.java)
- [ModelReviewController.java](../src/main/java/com/memento3d/modelreview/api/ModelReviewController.java)
- [PrintJobController.java](../src/main/java/com/memento3d/printjob/api/PrintJobController.java)
- [ResinProductionController.java](../src/main/java/com/memento3d/printjob/api/ResinProductionController.java)

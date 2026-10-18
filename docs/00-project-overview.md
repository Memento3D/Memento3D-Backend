# 00 — TỔNG QUAN DỰ ÁN MEMENTO3D (PROJECT OVERVIEW)

---

## 1. Memento3D Là Gì?

**Memento3D** là nền tảng điện toán đám mây kết hợp Trí tuệ Nhân tạo (AI Photogrammetry) và Hệ thống Sản xuất In 3D (Additive Manufacturing & WMS), cho phép người dùng chuyển hóa những hình ảnh hoặc video quay từ điện thoại thông minh thành mô hình 3D kỹ thuật số có độ chính xác cao, đồng thời đặt dịch vụ in 3D vật lý (tượng nhỏ, mô hình kỷ niệm, phụ kiện cá nhân hóa) được giao tận nơi.

- **Vấn đề thực tế (Problem):** Các phương pháp quét 3D truyền thống đòi hỏi thiết bị đắt tiền (máy quét laser hàng ngàn USD) cùng kỹ năng đồ họa 3D phức tạp để xử lý lưới đa giác (mesh topology) trước khi có thể in được.
- **Giải pháp (Solution):** Memento3D cung cấp một quy trình khép kín End-to-End:
  1. Hướng dẫn quét qua camera điện thoại (Guided Capture).
  2. Tự động kiểm tra chất lượng ảnh đầu vào (Automated QC).
  3. Tái tạo 3D bằng cụm máy chủ GPU (AI 3D Reconstruction via SfM + MVS).
  4. Trình xem 3D tương tác trên trình duyệt Web (Three.js WebGL Viewer).
  5. Định cấu hình chất liệu (PLA, PETG, Resin, Bronze Composite) và tính giá tự động.
  6. Thanh toán qua cổng điện tử nội địa (MoMo / ZaloPay).
  7. Điều phối sản xuất tại xưởng in 3D (OctoPrint / Local Printer Agent) và quản lý kho vật tư (WMS).

---

## 2. Đối Tượng Người Dùng & Các Tác Nhân Chính (Main Actors)

Nền tảng Memento3D quản lý 7 nhóm tác nhân thông qua phân quyền Spring Security (`ROLE_*`) và cổng người dùng chuyên biệt:

1. **Khách hàng (`ROLE_USER` / Customer):** Tải dữ liệu ảnh/video scan, chạy job tạo mô hình 3D, xem trước mô hình, chọn vật liệu và thanh toán đơn đặt in 3D cá nhân.
2. **Kỹ thuật viên xưởng in (`ROLE_OPERATOR` / Print Farm Operator):** Giám sát trạng thái máy in (nhiệt độ nozzle, bed, camera), quản lý hàng đợi lệnh in 3D, xuất vật liệu cho lệnh in, kiểm tra chất lượng thành phẩm.
3. **Chuyên viên kho vận (`ROLE_WAREHOUSE` / Warehouse Specialist):** Thực hiện Phiếu nhập kho (GRN), Phiếu xuất kho (GIN), kiểm soát tồn kho theo lô/cuộn filament, tra cứu thẻ kho và cảnh báo tồn kho an toàn.
4. **Quản lý điều hành cơ sở (`ROLE_MANAGER` / Operations Manager):** Quản trị kho khu vực, cấu hình danh mục vật liệu & định giá, duyệt giao dịch nhập/xuất kho, theo dõi toàn bộ đơn hàng và phân bổ sản xuất.
5. **Quản trị viên hệ thống (`ROLE_ADMIN` / System Administrator):** Toàn quyền kiểm trị hệ thống, quản lý tài khoản người dùng, giám sát Redis/hạ tầng, đăng ký máy in mới và cấp phát token cho Agent IoT.
6. **Đối tác gia công sản xuất (`ROLE_PROVIDER` / B2B Manufacturing Partner):** Tiếp nhận các lô đơn hàng in 3D được điều chuyển gia công ngoài, cập nhật trạng thái in, báo cáo QC và bàn giao hàng.
7. **Tài xế giao vận (`ROLE_DRIVER` / Delivery Driver):** Tiếp nhận kiện hàng từ cửa kho xuất (Dock), tối ưu lộ trình giao hàng, thu hộ COD và chụp ảnh/ký nhận bàn giao (Proof of Delivery).

---

## 3. Bảng Kiểm Toán Trạng Thái Triển Khai Hệ Thống (Module Audit Table)

*Quy ước trạng thái:*
- **IMPLEMENTED**: Đã triển khai đầy đủ cả Backend, Database và Frontend API.
- **PARTIAL**: Đã triển khai Backend / DB nhưng Frontend đang kết nối một phần, hoặc ngược lại.
- **MOCK**: Đang sử dụng cơ chế giả lập logic hoặc runner mô phỏng.
- **TODO**: Có kiến trúc trong thiết kế nhưng chưa viết mã nguồn hoàn chỉnh.
- **NOT FOUND**: Không tồn tại trong source code hiện tại.

| Phân Hệ / Module | Trạng Thái | Backend | Frontend | Database | API Endpoint | Ghi Chú Kỹ Thuật |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Identity & Authentication** | **IMPLEMENTED** | Java Spring Security, JJWT | React Context, Dual Token | `users`, `refresh_tokens`, `blacklisted_tokens` | `/api/v1/auth/**`, `/api/v1/users/**` | Đăng ký, đăng nhập email/pass, refresh token rotation, blacklist logout, Google OAuth2. |
| **Guided Capture & Upload** | **IMPLEMENTED** | Multipart chunked upload, Cloudinary | Resumable Upload Client | `upload_sessions`, `upload_chunks` | `/api/v1/uploads/**` | Hỗ trợ phân mảnh chunk upload, giới hạn quota 10GB/user, upload lên Cloudinary. |
| **3D Scan Session Management** | **IMPLEMENTED** | Scan Service & Access Guard | Scan Dashboard | `scans`, `scan_images`, `scan_videos` | `/api/v1/scans/**` | Quản lý phiên scan, tải ảnh (30-100 ảnh) hoặc video 360 độ (tối đa 2GB). |
| **Automated Quality Check (QC)**| **IMPLEMENTED** | OpenCV Laplacian, Mean Brightness, pHash | Image Inspection Viewer | `quality_check_runs`, `image_quality_results` | `/api/v1/quality-runs/**` | Chấm điểm mờ, phơi sáng, trùng góc chụp, cho phép Override điểm vàng (Amber). |
| **3D AI Reconstruction** | **PARTIAL** / **MOCK** | Spring Batch Scheduler, MockRunner, Dispatcher | Progress Poller | `reconstruction_jobs`, `model_outputs` | `/api/v1/reconstruction-jobs/**` | Tích hợp sẵn `MockReconstructionJobRunner` giả lập tiến độ 0-100% trong dev mode; sẵn sàng dispatch GPU cluster. |
| **3D Model Viewer & Rating** | **IMPLEMENTED** | Model Output & Rating Services | Three.js WebGL Viewer | `model_outputs`, `model_ratings` | `/api/v1/scans/{id}/outputs`, `/ratings` | Tải và hiển thị model GLB, STL, OBJ; người dùng đánh giá sao và gửi phản hồi. |
| **Orders & Commerce** | **IMPLEMENTED** | Order Service & State Machine | Customer Order Page | `orders` | `/api/v1/orders/**` | Tạo đơn hàng in 3D, cấu hình địa chỉ người nhận, trạng thái chuyển tiếp đơn hàng. |
| **Payment Gateway Integration** | **IMPLEMENTED** | MoMo & ZaloPay SDKs, Webhooks | Payment Callback Handler | `payment_transactions` | `/api/v1/payments/**`, `/webhooks/**` | Tạo URL thanh toán MoMo/ZaloPay, xử lý Webhook IPN với chữ ký HMAC-SHA256, hoàn tiền/hủy thanh toán. |
| **3D Printer Fleet & Agents** | **IMPLEMENTED** | Printer Service, Agent Gateway, Liveness | Machine Status Grid | `printers`, `printer_agents` | `/api/v1/printers/**`, `/api/v1/agent/**` | Quản lý máy in 3D, xác thực Local Agent bằng header `X-Agent-Token`, heartbeat tự động, giám sát liveness. |
| **Warehouse Management (WMS)** | **IMPLEMENTED** | Warehouse Service, Memberships | WMS Portal | `warehouses`, `warehouse_memberships` | `/api/v1/warehouses/**` | Quản lý kho khu vực, phân bổ thành viên kho (`WarehouseRole`), quản lý kích thước & sức chứa. |
| **Materials Catalog** | **IMPLEMENTED** | Material Service | Material Management | `materials` | `/api/v1/materials/**` | Danh mục vật liệu (PLA, PETG, Resin), thông số nhiệt độ đùn, mật độ `g/cm³`, định giá bán. |
| **Inventory & Lots Tracking** | **IMPLEMENTED** | Inventory Service, Double-Entry Ledger | Inventory Dashboard | `inventory_balances`, `inventory_transactions`, `stock_receipts`, `stock_issues`, `stock_reservations` | `/api/v1/inventory/**` | Quản lý tồn kho theo Lot/Batch, nhập kho GRN, xuất kho GIN, ghi sổ kép `InventoryTransactionType`. |
| **Print Jobs & Consumptions** | **IMPLEMENTED** | PrintJob Service, Material Reservation | Print Job Ops Page | `print_jobs`, `print_job_consumptions` | `/api/v1/print-jobs/**` | Khởi tạo lệnh in, giữ chỗ vật tư (`GIU_CHO`), xuất in (`XUAT_SAN_XUAT`), đối soát hao hụt thực tế. |
| **B2B Partner Portal** | **IMPLEMENTED** | Mock Order Dispatch | Provider Portal | Phân quyền `ROLE_PROVIDER` | `/provider/orders` | Nhận đơn hàng gia công, cập nhật giai đoạn sản xuất, xác nhận đạt chuẩn QC. |
| **Logistics & Driver Delivery** | **IMPLEMENTED** | Mock Delivery Route | Driver Portal | Phân quyền `ROLE_DRIVER` | `/driver/deliveries` | Điều phối lộ trình, tiếp nhận bưu kiện từ cửa kho xuất, cập nhật COD và ký nhận giao hàng. |
| **Booking & Passenger Trips** | **NOT FOUND** | Không tồn tại trong mã nguồn | Không có UI | Không có bảng DB | Không có API | Memento3D là nền tảng số hóa 3D & in ấn vật lý, không phải ứng dụng gọi xe/đặt chuyến. |
| **Redis Infrastructure** | **IMPLEMENTED** | RedisTemplate, Health Check Service | Redis Monitoring Badge | In-Memory (Port 6379) | `/api/v1/redis/health` | Cache thông tin user, cache trạng thái scan, blacklist access token khi đăng xuất. |

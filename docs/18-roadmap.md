# MEMENTO3D — PRODUCT ROADMAP & DEFINITION OF DONE

## 1. Development Phases & Roadmap

```mermaid
gantt
    title Memento3D Engineering Roadmap (2026 - 2027)
    dateFormat  YYYY-MM-DD
    section Phase 1: Stabilize
    JWT Blacklist & Security Hardening    :done, p1_1, 2026-08-01, 2026-09-15
    WMS Multi-warehouse & RBAC Fix         :done, p1_2, 2026-09-16, 2026-10-18
    Unit & Integration Test Suite (70%)    :active, p1_3, 2026-10-19, 2026-11-15
    section Phase 2: Hardware Farm
    OctoPrint IoT Agent Production Rollout :p2_1, 2026-11-01, 2026-12-15
    Obico AI Spaghetti Detection Hook     :p2_2, 2026-12-01, 2026-12-31
    Auto G-code Cloud Slicer Engine        :p2_3, 2027-01-01, 2027-02-15
    section Phase 3: AI & Scale
    Dedicated GPU Cluster (COLMAP/NeRF)    :p3_1, 2027-02-01, 2027-03-31
    Mesh Decimation & Auto Support Gen     :p3_2, 2027-03-15, 2027-04-30
    section Phase 4: Logistics
    Vietnamese Couriers (GHN / ViettelPost):p4_1, 2027-04-01, 2027-05-31
    Print Farm Partner Network Portal      :p4_2, 2027-05-01, 2027-06-30
```

---

## 2. Detailed Phase Breakdown

### Phase 1 — Stabilize & Solidify (Hiện tại — Q4/2026)
- **Mục tiêu**: Đảm bảo toàn bộ luồng nghiệp vụ hiện tại hoạt động trơn tru không lỗi, xử lý triệt để các vấn đề bảo mật và phân quyền.
- **Key Tasks**:
  - [x] Sửa lỗi phân quyền Kho cho Operator & Manager (`WarehouseSecurityService`).
  - [x] Tạo bộ tài liệu hoàn chỉnh `MEMENTO3D_DEVELOPER_GUIDE.md` và Developer Docs.
  - [ ] Bổ sung Unit Test cho luồng tính toán giá đơn hàng và trừ kho tự động.
  - [ ] Cấu hình rate limit bằng Redis Bucket4j trên các endpoint upload ảnh và login.

### Phase 2 — Production Hardware & IoT (Q4/2026 — Q1/2027)
- **Mục tiêu**: Kết nối thực tế với trang trại 20+ máy in 3D FDM & SLA thông qua bo mạch Raspberry Pi OctoPrint.
- **Key Tasks**:
  - [ ] Đóng gói Memento3D Python Agent thành file cài đặt `.deb` hoặc Docker image cho Raspberry Pi.
  - [ ] Hoàn thiện kết nối Webhook với Obico server để tự động dừng in khi phát hiện lỗi tưa sợi.
  - [ ] Tích hợp công cụ cắt lớp tự động trên Cloud (CuraEngine CLI / PrusaSlicer CLI) để biến file 3D STL thành file G-code mà không cần Operator can thiệp thủ công.

### Phase 3 — High-Performance AI Photogrammetry (Q1/2027 — Q2/2027)
- **Mục tiêu**: Rút ngắn thời gian tạo mô hình 3D từ 15 phút xuống dưới 3 phút với chất lượng siêu nét.
- **Key Tasks**:
  - [ ] Triển khai cụm worker GPU (NVIDIA A100 / RTX 4090) chạy mô hình 3D Gaussian Splatting và NeRF.
  - [ ] Xây dựng thuật toán tự động làm kín lỗ hổng (Watertight manifold meshing) để file 3D luôn luôn in được mà không bị rỗng bề mặt.

### Phase 4 — Logistics & Partner Ecosystem (Q2/2027)
- **Mục tiêu**: Tự động hóa khâu giao vận và mở rộng mạng lưới xưởng in gia công vệ tinh.
- **Key Tasks**:
  - [ ] Tích hợp API tạo vận đơn và in mã vận đơn tự động với Giao Hàng Nhanh (GHN) và Viettel Post.
  - [ ] Hoàn thiện Provider Portal cho phép các xưởng in ngoài nhận gia công đơn hàng khi xưởng chính quá tải.

---

## 3. Definition of Done (DoD)

Một User Story hoặc Feature chỉ được coi là **DONE** và sẵn sàng merge vào nhánh `main` khi đáp ứng đủ 10 tiêu chí:

1. [ ] **Database Design**: Bảng được thiết kế chuẩn hóa 3NF, có đầy đủ Foreign Keys, Unique Constraints, và Migration script (Flyway).
2. [ ] **Security**: Kiểm tra quyền truy cập chặt chẽ (`@PreAuthorize`), bảo vệ phân quyền dữ liệu cá nhân (Ownership guard), không rò rỉ dữ liệu.
3. [ ] **Service Logic**: Viết trong Service có `@Transactional`, không để logic nghiệp vụ rò rỉ ra ngoài Controller.
4. [ ] **Data Mapping**: 100% sử dụng MapStruct, không trả Entity trực tiếp ra API.
5. [ ] **Validation**: Có Jakarta Validation (`@NotNull`, `@NotBlank`, `@Min`, v.v.) trên mọi Request DTO.
6. [ ] **Audit Trail**: Ghi nhận thời gian tạo/sửa (`createdAt`, `updatedAt`, `createdBy`).
7. [ ] **OpenAPI / Swagger**: Đầy đủ annotations `@Operation`, `@ApiResponse`, hiển thị chuẩn trên Swagger UI.
8. [ ] **Frontend Integration**: Tích hợp màn hình hoàn chỉnh, có Loading state, Empty state, và Toast thông báo lỗi rõ ràng.
9. [ ] **Testing**: Đạt tối thiểu 70% Unit Test coverage cho phần Service mới viết.
10. [ ] **Documentation**: Cập nhật thông tin endpoint vào `docs/04-api-reference.md`.

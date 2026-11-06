# Customization MVP — bước 2

Đã nối DTO/client/finalizer với manifest và phụ kiện fit từ AI. Kiểm tra job/attempt, base SHA, catalog/profile pin, asset version/source SHA và matrix glTF column-major. Geometry đã fit dùng matrix identity. Prepared fit ràng buộc user/scan/job/attempt, không được dùng lại cho ngữ cảnh khác.

Mũ tham chiếu asset chung + matrix riêng. Tóc/kính nằm ở memento3d/users/{user}/scans/{scan}/jobs/{job}/attempts/{attempt}/customization/{asset}.glb, không ghi đè preview.glb. HTTP/upload nằm ngoài transaction; fit metadata, ModelOutput, job.done và outbox commit cùng nhau. Hủy/đổi attempt hoặc dữ liệu không khớp không được công bố thành công.

V28 thêm output_kind/customization_ready và hai bảng customization_fit_results/customization_fit_assets. Model nền custom không tự trở thành DigitalAsset sẵn sàng sản xuất hoặc yêu cầu duyệt như model ghép hoàn chỉnh. Không lưu signed URL vào fit metadata.

Catalog mvp-v1 phải được import/ACTIVE qua bước 1 trước khi finalize custom. Nếu chưa có catalog, scheduler chưa công bố done và thử lưu lại, không chạy lại AI.

52 test liên quan đã đạt trước rà soát cuối. Theo yêu cầu ưu tiên hoàn thiện, không mở thêm vòng test/container; lần cuối chỉ compile source và test. Chưa kiểm chứng V28 trên PostgreSQL hoặc chạy xuyên Spring/ngrok/AI/B2 thật. Không áp dụng Neon, không deploy BE.

Tiếp theo: API scene có kiểm tra quyền và URL tải mới; draft JSON có revision để lưu/mở lại. Chưa thay FE, checkout hoặc snapshot/export đơn in.

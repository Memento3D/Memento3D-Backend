# Memento3D — Định hướng thiết kế Input Validation và Quality Check

## 1. Mục tiêu

Ưu tiên hoàn thành luồng upload ảnh → generate model 3D → lưu Cloudinary → trả output.

Sau khi luồng hoạt động ổn định, bổ sung các quy tắc kiểm tra thông qua interface và các implementation riêng, hạn chế sửa logic điều phối của service.

Tài liệu này là định hướng thiết kế, chưa có nghĩa các thành phần đã được triển khai.

## 2. Quyết định ở giai đoạn hiện tại

- Sử dụng ảnh test đã biết là phù hợp với pipeline.
- Service Generate chưa bổ sung kiểm tra chất lượng input.
- Chưa triển khai đầy đủ các rules kiểm tra từng ảnh hoặc cả bộ ảnh.
- Giữ các kiểm tra và ràng buộc hiện có của hệ thống.
- Pipeline AI vẫn có thể từ chối ảnh theo các kiểm tra đang có, chẳng hạn IMAGE_TOO_SMALL.

Việc chưa chạy Quality Check phải được hiểu là “chưa kiểm tra” hoặc
“bỏ qua trong giai đoạn test”, không đồng nghĩa ảnh đã vượt qua QC.

Luồng hiện tại:

Upload → Generate → Spring lấy input → tạo job/snapshot
→ AI xử lý → upload Cloudinary → lưu output → job done.

Chỉ đánh dấu done khi output đã được lưu thành công và dữ liệu
liên quan đã được cập nhật trong database.

## 3. Luồng mục tiêu khi bổ sung Quality Check

Upload
→ kiểm tra từng file
→ lưu ảnh và metadata
→ người dùng bấm Generate
→ Spring lấy bộ input
→ kiểm tra tổng thể
→ tạo job và snapshot
→ AI xử lý
→ upload Cloudinary
→ lưu output
→ job done.

Người dùng chỉ cần upload rồi bấm Generate. Việc xác định những
ảnh nào được đưa vào job là trách nhiệm của backend theo chính
sách đã thống nhất, không bắt buộc người dùng chọn lại từng ảnh.

Lưu ý: cho phép upload nhiều ảnh không đồng nghĩa model AI hiện
tại đã hỗ trợ dùng nhiều ảnh để generate. Cần xác nhận khả năng
của engine trước khi triển khai luồng multi-image.

## 4. Phân chia trách nhiệm

### Controller

- Nhận request và trả response.
- Xử lý cấu trúc request, authentication và các thông tin cần thiết.
- Gọi service.
- Không chứa thuật toán kiểm tra chất lượng ảnh.

### Upload service

- Điều phối việc nhận và lưu file.
- Gọi thành phần kiểm tra từng file khi được triển khai.
- Cập nhật trạng thái upload và metadata.

### Generate service

- Lấy input thuộc scan.
- Gọi thành phần kiểm tra tổng thể khi được triển khai.
- Quyết định có được tạo job hay không.
- Tạo snapshot và job.
- Không chứa trực tiếp thuật toán của từng rule.

### Validation / Quality Check implementation

- Đọc input và thực hiện kiểm tra.
- Trả kết quả có cấu trúc.
- Không tự đổi trạng thái scan hoặc job.
- Không tự upload, xóa file hoặc tạo job.

### AI service

- Thực hiện pipeline generate.
- Có thể thực hiện các kiểm tra kỹ thuật chuyên sâu cần Python,
  thư viện xử lý ảnh hoặc GPU.
- Trả lỗi và kết quả về Spring.

## 5. Hướng thiết kế interface

Đề xuất hai interface theo phạm vi kiểm tra:

- UploadFileValidator: kiểm tra một file ở bước upload.
- GenerationInputValidator: kiểm tra bộ input trước khi tạo job.

Ví dụ minh họa, chưa phải code hoàn chỉnh:

    public interface UploadFileValidator {
        ValidationResult validate(UploadFileContext context);
    }

    public interface GenerationInputValidator {
        ValidationResult validate(GenerationInputContext context);
    }

Context cung cấp dữ liệu cần cho kiểm tra, chẳng hạn:

- Thông tin file và metadata.
- Danh sách ảnh trong bộ input.
- Cấu hình engine hoặc chính sách kiểm tra.
- Phiên bản input khi cần phát hiện dữ liệu thay đổi.

Không nên đưa quyền sửa entity vào validator. Validator chỉ đọc
dữ liệu và trả kết quả; service chịu trách nhiệm cập nhật dữ liệu.

## 6. Cấu trúc kết quả kiểm tra

ValidationResult nên phân biệt:

- PASSED: đã kiểm tra và đáp ứng các rules được áp dụng.
- FAILED: đã kiểm tra và có rule không đạt.
- SKIPPED: chưa chạy kiểm tra hoặc được cấu hình bỏ qua.

Kết quả có thể bao gồm:

- Danh sách lỗi hoặc cảnh báo.
- Mã lỗi ổn định.
- Thông báo để hiển thị.
- ID ảnh liên quan nếu lỗi thuộc một ảnh.
- Tên rule và phiên bản rule khi cần truy vết.

Lỗi kỹ thuật như timeout, mất mạng hoặc không đọc được file cần
được phân biệt với kết luận “ảnh không đạt chất lượng”.

Chính sách cho phép tiếp tục hay chặn generate là quyết định
riêng của service/policy, dựa trên kết quả kiểm tra.

Nếu dùng implementation bỏ qua trong giai đoạn test, implementation
đó trả SKIPPED. Cấu hình test có thể cho phép tiếp tục với SKIPPED.

## 7. Cách thêm rules sau này

Khi cần bổ sung kiểm tra:

1. Xác định rule áp dụng cho một file hay cả bộ input.
2. Xác định dữ liệu rule cần đọc.
3. Tạo implementation tương ứng.
4. Trả kết quả theo contract chung.
5. Đăng ký implementation vào luồng kiểm tra.
6. Kiểm thử rule và ảnh hưởng tới luồng generate.

Khi số lượng rules tăng, có thể dùng danh sách các rule:

    Upload service
        → UploadFileValidator
            → các rule kiểm tra file

    Generate service
        → GenerationInputValidator
            → các rule kiểm tra bộ input

Chưa cần xây dựng một rule engine phức tạp ngay từ đầu.
Chỉ bổ sung cơ chế phối hợp nhiều rules khi có nhu cầu thực tế.

## 8. Đặt rules ở bước nào?

Interface giúp tách logic và thay implementation dễ hơn, nhưng
không có nghĩa mọi rule có thể đặt ở bất kỳ bước nào.

- Rule kiểm tra file cần có dữ liệu file.
- Rule kiểm tra tính nhất quán của bộ ảnh cần có cả bộ input.
- Rule phụ thuộc engine cần biết engine được sử dụng.
- Rule tốn nhiều thời gian có thể cần xử lý bất đồng bộ.

Nếu chuyển một rule sang bước khác, phải bảo đảm bước đó có đủ
context và kết quả vẫn được áp dụng trước hành động cần bảo vệ.

Các kiểm tra lâu hoặc gọi dịch vụ bên ngoài nên chạy ngoài
transaction giữ khóa database.

## 9. Bảo vệ tính nhất quán dữ liệu

Interface hỗ trợ mở rộng; tính nhất quán dữ liệu còn phụ thuộc
service, transaction, snapshot và ràng buộc database.

Các nguyên tắc cần giữ:

- Input dùng để kiểm tra phải tương ứng với input đưa vào job.
- Nếu input thay đổi sau khi kiểm tra, kết quả cũ không tự động
  được áp dụng cho bộ input mới.
- Snapshot ghi lại input thực sự được sử dụng.
- Không lưu secrets hoặc URL ký tạm thời vào snapshot.
- Validator không tự thay đổi dữ liệu nghiệp vụ.
- Cập nhật trạng thái job/scan do service điều phối.
- Phản hồi AI phải khớp job và attempt hiện tại.
- Retry phải xác định rõ dùng lại snapshot cũ hay tạo job mới
  với input mới.
- Lỗi upload output không được khiến hệ thống báo done.

Khi có kiểm tra kéo dài, cần cơ chế xác nhận input chưa thay đổi
trước khi tạo job, chẳng hạn phiên bản input hoặc snapshot bất biến.

## 10. Có cần API Quality Check riêng không?

Không bắt buộc.

Ở giai đoạn đầu, service Upload và Generate có thể gọi trực tiếp
các interface kiểm tra trong backend.

Chỉ cần API Quality Check riêng khi sản phẩm có nhu cầu như:

- Người dùng chủ động kiểm tra trước khi generate.
- Kiểm tra chạy lâu và cần theo dõi tiến độ.
- Xem kết quả kiểm tra chi tiết.
- Kiểm tra lại sau khi bổ sung ảnh.
- Tái sử dụng một kết quả kiểm tra đã lưu.

Dù có API riêng, logic kiểm tra vẫn nằm trong service/implementation.
Controller chỉ là nơi cung cấp khả năng gọi chức năng đó.

Model ratings là phản hồi về model sau generate, không thay thế
Quality Check của input.

## 11. Những quyết định còn để mở

- Engine sẽ sử dụng một ảnh hay nhiều ảnh.
- Số lượng ảnh tối thiểu/tối đa cho từng engine.
- Backend lấy toàn bộ ảnh hợp lệ hay chọn một tập con.
- Các rules nào chặn generate, rules nào chỉ cảnh báo.
- Có lưu lịch sử Quality Check hay không.
- Khi ảnh thay đổi, kết quả QC nào cần bị vô hiệu hóa.
- Có cần xử lý QC bất đồng bộ hay API riêng.
- Chính sách retry khi input không đạt.

Không chốt số lượng ảnh trước khi xác nhận yêu cầu của engine
và cách generate thực tế.

## 12. Checklist bổ sung sau khi hoàn thành luồng

### Kiến trúc

- [ ] Logic kiểm tra tách khỏi controller.
- [ ] Service điều phối, validator chỉ trả kết quả.
- [ ] Có contract riêng cho kiểm tra file và bộ input.
- [ ] Phân biệt PASSED, FAILED và SKIPPED.
- [ ] Phân biệt lỗi kỹ thuật với lỗi chất lượng.
- [ ] Có thể thêm rule mà không sửa thuật toán trong Generate service.

### Dữ liệu

- [ ] Bộ input kiểm tra khớp snapshot của job.
- [ ] Phát hiện được input thay đổi sau QC.
- [ ] Kết quả QC cũ không áp dụng sai cho input mới.
- [ ] Retry có chính sách snapshot rõ ràng.
- [ ] Trạng thái done chỉ xuất hiện sau khi lưu output thành công.

### Kiểm thử

- [ ] Input hợp lệ đi hết luồng.
- [ ] Input không hợp lệ trả lỗi rõ ràng.
- [ ] Bỏ qua QC không bị ghi nhận thành PASSED.
- [ ] Thêm/xóa ảnh trong lúc kiểm tra được xử lý nhất quán.
- [ ] Lỗi kỹ thuật không bị diễn giải thành ảnh kém chất lượng.
- [ ] Thêm rule mới không làm hỏng luồng hiện có.

## 13. Thứ tự triển khai

1. Hoàn thiện và xác nhận luồng upload → generate → output.
2. Bổ sung interface và contract kết quả kiểm tra.
3. Triển khai từng rule theo nhu cầu đã được xác nhận.
4. Bổ sung snapshot/version và lưu kết quả QC khi cần.
5. Mở rộng multi-image sau khi engine và chính sách input được chốt.

Ưu tiên hiện tại là hoàn thành luồng. Các rules chất lượng sẽ
được bổ sung sau dựa trên thiết kế này.
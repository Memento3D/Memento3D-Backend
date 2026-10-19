-- ============================================================================
-- Migration: V16__customer_registration_and_legal_consents.sql
-- Description: Add email verification, user status, user identities, legal documents,
--              user consents, and email verification tokens for Memento3D.
-- ============================================================================

-- 1. Update users table with email_verified and status
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE' NOT NULL;

-- Ensure existing users with Google provider or already created have verified email & ACTIVE status
UPDATE public.users SET email_verified = TRUE WHERE provider = 'GOOGLE' OR email_verified IS NULL;
UPDATE public.users SET status = 'ACTIVE' WHERE status IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_status ON public.users (status);
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON public.users (email_verified);

-- 2. User Identities table (supports multi-provider linking: LOCAL, GOOGLE, etc.)
CREATE TABLE IF NOT EXISTS public.user_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_identities_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX IF NOT EXISTS idx_user_identities_user_id ON public.user_identities (user_id);
CREATE INDEX IF NOT EXISTS idx_user_identities_provider_subject ON public.user_identities (provider, provider_subject);

-- Backfill user_identities for existing users
INSERT INTO public.user_identities (user_id, provider, provider_subject, linked_at)
SELECT id, provider, COALESCE(provider_id, email), created_at
FROM public.users
WHERE id NOT IN (SELECT user_id FROM public.user_identities)
ON CONFLICT (provider, provider_subject) DO NOTHING;

-- 3. Email Verification Tokens table
CREATE TABLE IF NOT EXISTS public.email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user_id ON public.email_verification_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_token ON public.email_verification_tokens (token);

-- 4. Legal Documents table (Terms of Service, Privacy Policy)
CREATE TABLE IF NOT EXISTS public.legal_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_legal_documents_type_version UNIQUE (type, version)
);

CREATE INDEX IF NOT EXISTS idx_legal_documents_type_active ON public.legal_documents (type, active);

-- 5. User Consents table (Audit trail for agreements & consents)
CREATE TABLE IF NOT EXISTS public.user_consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    document_id UUID REFERENCES public.legal_documents(id) ON DELETE SET NULL,
    document_type VARCHAR(50) NOT NULL,
    document_version VARCHAR(20) NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT TRUE,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consent_source VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_user_consents_user_id ON public.user_consents (user_id);
CREATE INDEX IF NOT EXISTS idx_user_consents_type ON public.user_consents (consent_type);

-- 6. Seed Initial Active Legal Documents (Version 1.0)
INSERT INTO public.legal_documents (id, type, version, title, content, effective_at, active, created_at, updated_at)
VALUES (
    'a1000000-0000-0000-0000-000000000001',
    'TERMS',
    '1.0',
    'MEMENTO3D — ĐIỀU KHOẢN SỬ DỤNG VÀ THỎA THUẬN TÀI KHOẢN',
    '# MEMENTO3D — ĐIỀU KHOẢN SỬ DỤNG VÀ THỎA THUẬN TÀI KHOẢN

**Phiên bản dự thảo: 1.0 | Ngày soạn: 19/10/2026**

## 1. Giới thiệu và phạm vi áp dụng
Memento3D là nền tảng giúp người dùng khám phá, đặt mua sản phẩm quà tặng cá nhân hóa, gửi hình ảnh để tạo bản xem trước và đặt dịch vụ tạo mô hình 3D/in 3D theo phạm vi tính năng thực tế được cung cấp. Điều khoản này áp dụng cho khách hàng đăng ký, đăng nhập, sử dụng tài khoản và dịch vụ tại website Memento3D.

## 2. Điều kiện đăng ký tài khoản
Người dùng cung cấp thông tin chính xác, hợp pháp, có quyền sử dụng địa chỉ email đăng ký, và có đủ năng lực thực hiện giao dịch theo quy định áp dụng. Với người chưa thành niên hoặc bị hạn chế năng lực hành vi, cần sự tham gia/đồng ý của người đại diện khi pháp luật yêu cầu. Tài khoản tạo qua form hoặc qua Google đăng nhập lần đầu được gán vai trò Customer, không tự có quyền quản trị.

## 3. Phương thức đăng ký và bảo mật
Người dùng có thể đăng ký bằng email/mật khẩu hoặc sử dụng Google OAuth2/OIDC. Khi đăng ký bằng Google, Memento3D chỉ tiếp nhận các dữ liệu tài khoản được hiển thị trong Chính sách quyền riêng tư và các quyền truy cập đã thông báo. Người dùng có trách nhiệm bảo vệ thông tin đăng nhập, thông báo ngay cho Memento3D nếu nghi ngờ tài khoản bị truy cập trái phép. Memento3D áp dụng xác minh email, giới hạn số lần đăng nhập và cơ chế bảo vệ tài khoản phù hợp.

## 4. Chấp nhận điều khoản và quản lý sự đồng ý
Trước khi tạo tài khoản, người dùng được cung cấp Điều khoản sử dụng và Chính sách quyền riêng tư để xem. Người dùng phải chủ động đánh dấu đồng ý với Điều khoản sử dụng; ô chọn không được tích sẵn. Hệ thống ghi nhận phiên bản điều khoản, thời điểm và phương thức chấp nhận để quản lý bằng chứng giao dịch. Các đồng ý xử lý dữ liệu cá nhân cần thiết được thông báo và thu thập riêng khi pháp luật yêu cầu. Đồng ý nhận quảng cáo/ưu đãi là tùy chọn, được quản lý độc lập và có thể rút lại.

## 5. Quyền và nghĩa vụ của người dùng
Người dùng có thể truy cập, cập nhật các thông tin tài khoản được hỗ trợ; xem lịch sử đơn hàng, yêu cầu hỗ trợ, thực hiện quyền đối với dữ liệu cá nhân theo Chính sách quyền riêng tư và pháp luật áp dụng. Người dùng không được giả mạo danh tính, can thiệp hệ thống, phát tán mã độc, tải nội dung bất hợp pháp hoặc xâm phạm quyền của người khác.

## 6. Hình ảnh tải lên và quyền sử dụng nội dung
Người dùng cam kết có quyền sử dụng hình ảnh, chân dung và tài liệu đã tải lên, bao gồm sự cho phép cần thiết của người có trong ảnh khi pháp luật yêu cầu. Người dùng không được cung cấp nội dung xâm phạm sở hữu trí tuệ, quyền riêng tư hoặc danh dự người khác. Trong phạm vi cần thiết để thực hiện yêu cầu của người dùng, Memento3D và các đối tác xử lý phù hợp được phép xử lý nội dung theo mục đích và thời hạn mô tả tại Chính sách quyền riêng tư. Việc dùng ảnh/mô hình cho quảng cáo, huấn luyện AI hoặc công bố công khai không được mặc nhiên suy ra từ việc đăng ký tài khoản.

## 7. Bản xem trước, mô hình 3D và giới hạn kỹ thuật
Kết quả dựng mô hình 3D phụ thuộc chất lượng, số lượng, góc chụp và đặc điểm dữ liệu đầu vào. Bản xem trước miễn phí có thể giảm độ phân giải, có watermark và giới hạn lượt xử lý nếu các giới hạn đó được hiển thị rõ trước khi sử dụng. Memento3D không bảo đảm sản phẩm số hoặc vật lý trùng khớp tuyệt đối với ảnh ban đầu; các đặc tính, dung sai và lựa chọn vật liệu sẽ được công bố tại bước đặt hàng.

## 8. Sản phẩm, giá và đặt hàng
Mô tả, giá niêm yết, tùy chọn kích thước/vật liệu, phụ kiện, phí xử lý, phí vận chuyển và tổng số tiền phải thanh toán được thể hiện ở trang sản phẩm hoặc trước bước xác nhận đơn. Đơn hàng chỉ được ghi nhận theo trạng thái và thông báo xác nhận của hệ thống. Nếu một giá trị hiển thị sai do lỗi kỹ thuật, Memento3D sẽ thông báo và xử lý minh bạch trước khi tiếp tục thực hiện giao dịch, phù hợp pháp luật.

## 9. Thanh toán, giao hàng, đổi trả và hoàn tiền
Phương thức thanh toán, đơn vị nhận tiền, điều kiện hủy/đổi trả, phí phát sinh, thời gian giao hàng dự kiến và cơ chế hoàn tiền được công bố trước khi người dùng xác nhận đặt hàng và tại các chính sách riêng. Với sản phẩm cá nhân hóa hoặc làm theo yêu cầu, những giới hạn đổi trả (nếu có) phải được thông báo trước và không làm mất quyền bắt buộc của người tiêu dùng theo pháp luật.

## 10. Dữ liệu cá nhân và quyền riêng tư
Memento3D xử lý dữ liệu cá nhân theo mục đích được thông báo, gồm quản lý tài khoản, xác thực đăng nhập, hỗ trợ khách hàng và thực hiện đơn hàng, cùng những mục đích khác nếu có cơ sở pháp lý phù hợp. Các loại dữ liệu, bên nhận dữ liệu, thời gian lưu giữ, cách thực hiện quyền chủ thể dữ liệu và thông tin liên hệ được công bố tại Chính sách quyền riêng tư.

## 11. Tạm ngừng hoặc chấm dứt tài khoản
Người dùng có thể yêu cầu khóa hoặc xóa tài khoản thông qua kênh hỗ trợ. Memento3D có thể tạm ngừng tài khoản khi có dấu hiệu lạm dụng, xâm nhập, gian lận hoặc vi phạm nghiêm trọng, với thông báo và cơ chế khiếu nại thích hợp, trừ trường hợp phải hành động khẩn cấp để bảo vệ an toàn hệ thống.

## 12. Thông báo và thay đổi điều khoản
Memento3D có thể cập nhật Điều khoản sử dụng để phản ánh thay đổi chức năng, quy trình hoặc yêu cầu pháp luật. Phiên bản và ngày áp dụng được công bố. Với thay đổi quan trọng cần chấp thuận lại, hệ thống sẽ chủ động yêu cầu người dùng xem và đồng ý trước khi tiếp tục sử dụng chức năng liên quan.

## 13. Hỗ trợ và giải quyết khiếu nại
Người dùng có thể gửi yêu cầu, phản ánh hoặc khiếu nại qua email hỗ trợ support@memento3d.vn hoặc kênh hỗ trợ trực tuyến. Memento3D sẽ tiếp nhận, phản hồi và xử lý theo thời hạn công bố, tuân thủ pháp luật.

## 14. Điều khoản cuối cùng
Nếu một phần điều khoản không có hiệu lực theo pháp luật, các điều khoản còn lại vẫn được áp dụng trong giới hạn cho phép. Điều khoản này được giải thích theo pháp luật Việt Nam.',
    NOW(),
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (type, version) DO UPDATE 
SET content = EXCLUDED.content, title = EXCLUDED.title, updated_at = NOW();

INSERT INTO public.legal_documents (id, type, version, title, content, effective_at, active, created_at, updated_at)
VALUES (
    'a1000000-0000-0000-0000-000000000002',
    'PRIVACY',
    '1.0',
    'MEMENTO3D — CHÍNH SÁCH QUYỀN RIÊNG TƯ VÀ BẢO VỆ DỮ LIỆU CÁ NHÂN',
    '# MEMENTO3D — CHÍNH SÁCH QUYỀN RIÊNG TƯ VÀ BẢO VỆ DỮ LIỆU CÁ NHÂN

**Phiên bản: 1.0 | Ngày ban hành: 19/10/2026**

## 1. Mục đích và phạm vi thu thập dữ liệu
Memento3D cam kết bảo vệ quyền riêng tư và dữ liệu cá nhân của người dùng tuân theo Nghị định 13/2023/NĐ-CP về bảo vệ dữ liệu cá nhân và các quy định pháp luật hiện hành.

Dữ liệu chúng tôi thu thập bao gồm:
- **Thông tin tài khoản cơ bản**: Họ và tên, địa chỉ email, ảnh đại diện (nếu đăng nhập bằng Google hoặc tải lên), mật khẩu đã được mã hóa một chiều (bcrypt).
- **Hình ảnh và tư liệu số**: Các bức ảnh chân dung hoặc hình ảnh do khách hàng tải lên với mục đích dựng bản xem trước 3D và in sản phẩm quà tặng.
- **Thông tin giao dịch và giao nhận**: Địa chỉ nhận hàng, số điện thoại người nhận, lịch sử đơn hàng và thanh toán.
- **Thông tin kỹ thuật và nhật ký bảo mật**: Địa chỉ IP, chuỗi User-Agent, thời điểm đồng ý điều khoản, nhằm mục đích kiểm toán bảo mật và phòng chống gian lận.

## 2. Mục đích xử lý dữ liệu
Dữ liệu cá nhân chỉ được thu thập và xử lý cho các mục đích hợp pháp sau:
1. Đăng ký, xác minh danh tính và duy trì tài khoản Memento3D.
2. Xử lý thuật toán AI tái tạo mô hình 3D từ các góc ảnh người dùng cung cấp.
3. Sản xuất, gia công in 3D, đóng gói và vận chuyển đơn hàng đến địa chỉ yêu cầu.
4. Hỗ trợ khách hàng, giải quyết khiếu nại và hoàn tiền.
5. Gửi thông tin khuyến mãi, ưu đãi (chỉ khi người dùng đã chủ động đồng ý và có thể hủy bất kỳ lúc nào).
6. Tuân thủ nghĩa vụ lưu trữ chứng từ kế toán, thuế và quy định pháp luật.

## 3. Thời hạn lưu trữ dữ liệu
- Thông tin tài khoản: Được lưu trữ trong suốt thời gian tài khoản hoạt động hoặc cho đến khi người dùng yêu cầu xóa tài khoản.
- Hình ảnh tải lên & File mô hình 3D tạm thời: Lưu trữ trong vòng 30 ngày kể từ khi tạo bản xem trước, sau đó tự động lưu trữ dài hạn hoặc xóa theo chính sách bộ nhớ người dùng.
- Nhật ký đồng ý và kiểm toán bảo mật: Lưu trữ tối thiểu theo quy định chứng từ điện tử nhằm bảo vệ quyền lợi pháp lý.

## 4. Quyền của chủ thể dữ liệu
Người dùng có đầy đủ các quyền:
- Quyền được biết và quyền đồng ý đối với dữ liệu của mình.
- Quyền truy cập, chỉnh sửa hoặc yêu cầu cung cấp bản sao dữ liệu cá nhân.
- Quyền rút lại sự đồng ý (đặc biệt đối với mục đích tiếp thị và quảng cáo).
- Quyền yêu cầu xóa dữ liệu hoặc khóa tài khoản cá nhân.
- Quyền khiếu nại và phản ánh đến bộ phận phụ trách bảo vệ dữ liệu của Memento3D.

## 5. Biện pháp bảo vệ và an toàn thông tin
Memento3D áp dụng các tiêu chuẩn an ninh nghiêm ngặt: mã hóa truyền tải TLS/HTTPS, băm mật khẩu bằng BCrypt cost >= 10, phân quyền kiểm soát truy cập theo vai trò (RBAC), kiểm toán định kỳ và lưu trữ bảo mật trên hạ tầng điện toán đám mây.

## 6. Thông tin liên hệ
Mọi thắc mắc hoặc yêu cầu liên quan đến dữ liệu cá nhân, vui lòng liên hệ:
- **Bộ phận Bảo vệ Dữ liệu Memento3D**
- **Email**: privacy@memento3d.vn / support@memento3d.vn
- **Địa chỉ**: Tầng 5, Tòa nhà Công nghệ Memento3D, Việt Nam.',
    NOW(),
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (type, version) DO UPDATE 
SET content = EXCLUDED.content, title = EXCLUDED.title, updated_at = NOW();

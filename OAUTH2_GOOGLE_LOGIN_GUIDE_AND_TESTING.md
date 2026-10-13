# 📖 Tài Liệu Hướng Dẫn Tích Hợp & Kiểm Thử Đăng Nhập Google OAuth2 / OIDC — Memento3D Backend

> **Tài liệu chuẩn dành cho Developer, Frontend & QA**: Hướng dẫn chi tiết kiến trúc xác thực OAuth2 / OpenID Connect, cấu hình Google Cloud Console, đặc tả endpoints, kịch bản test trình duyệt, cURL / Postman, mẫu code tích hợp Frontend, và xử lý sự cố.

---

## 📑 Mục Lục
1. [Tổng Quan Kiến Trúc Đăng Nhập Google OAuth2](#1-tổng-quan-kiến-trúc-đăng-nhập-google-oauth2)
2. [Cấu Hình Môi Trường & Google Cloud Console](#2-cấu-hình-môi-trường--google-cloud-console)
3. [Đặc Tả Endpoints Backend](#3-đặc-tả-endpoints-backend)
4. [Hướng Dẫn Kiểm Thử Từng Bước (Testing Walkthrough)](#4-hướng-dẫn-kiểm-thử-từng-bước-testing-walkthrough)
5. [Mẫu Code Tích Hợp Phía Frontend (React / SPA / HTML)](#5-mẫu-code-tích-hợp-phía-frontend-react--spa--html)
6. [Hướng Dẫn Kiểm Thử Tự Động (Automated Unit Tests)](#6-hướng-dẫn-kiểm-thử-tự-động-automated-unit-tests)
7. [Các Cơ Chế Bảo Mật Nổi Bật](#7-các-cơ-chế-bảo-mật-nổi-bật)
8. [Troubleshooting & Các Lỗi Thường Gặp](#8-troubleshooting--các-lỗi-thường-gặp)

---

## 1. Tổng Quan Kiến Trúc Đăng Nhập Google OAuth2

Hệ thống xác thực của **Memento3D Backend** sử dụng chuẩn **OAuth2 Authorization Code Flow** kết hợp **OpenID Connect (OIDC)**, chuyển đổi danh tính Google thành phiên làm việc **JWT Stateless (Access Token + Refresh Token)** đồng nhất với hệ sinh thái API hiện có.

### 🌟 Các Điểm Nổi Bật:
- **Stateless Authorization Request Repository (`HttpCookieOAuth2AuthorizationRequestRepository`)**: Do ứng dụng cấu hình `SessionCreationPolicy.STATELESS`, request và mã CSRF state được lưu an toàn trong HttpOnly Cookie có thời hạn 180s thay vì phụ thuộc vào `HttpSession`.
- **Thông Tin Đa Năng (`CustomUserDetails`)**: Triển khai đồng thời `UserDetails` và `OAuth2User`, giúp các Controller sử dụng `@AuthenticationPrincipal CustomUserDetails` hoạt động nhất quán dù đăng nhập bằng mật khẩu hay Google.
- **Tự Động Liên Kết Tài Khoản (Smart Account Linking)**: Nếu người dùng đã đăng ký trước đó bằng Email & Mật khẩu cục bộ, khi đăng nhập qua Google với cùng email, hệ thống sẽ tự động cập nhật `providerId`, `avatarUrl` mà không làm mất dữ liệu scans hay đơn hàng trước đó.
- **Migration Tương Thích (`V6__add_oauth2_fields_to_users.sql`)**: Cập nhật schema PostgreSQL với các trường `provider`, `provider_id`, `avatar_url` và đổi `password` sang NULLABLE để hỗ trợ tài khoản thuần OAuth2.

---

### 🔄 Sơ Đồ Luồng Hoạt Động (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant FE as Frontend (SPA http://localhost:3000)
    participant BE as Backend Spring Boot (:8080)
    participant Google as Google OAuth2 Server
    participant DB as PostgreSQL DB

    User->>FE: Bấm nút "Đăng nhập bằng Google"
    FE->>BE: GET /oauth2/authorization/google (kèm redirect_uri cookie nếu có)
    BE-->>User: 302 Redirect sang accounts.google.com kèm state & client_id
    User->>Google: Nhập tài khoản, mật khẩu & đồng ý cấp quyền (email, profile)
    Google-->>BE: 302 Redirect về /login/oauth2/code/google?code=AUTH_CODE&state=STATE
    BE->>BE: HttpCookieRepository xác thực state chống CSRF
    BE->>Google: Gửi Authorization Code đổi lấy ID Token & User Info
    Google-->>BE: Trả về Profile (sub, email, name, picture)
    BE->>DB: Tìm kiếm user theo email
    alt User chưa có trong DB
        BE->>DB: INSERT user mới (provider=GOOGLE, role=ROLE_USER, avatar_url)
    else User đã tồn tại
        BE->>DB: UPDATE user (cập nhật avatar_url, fullName, providerId)
    end
    BE->>BE: JwtService sinh JWT Access Token + RefreshTokenService sinh Refresh Token
    BE-->>FE: 302 Redirect về: http://localhost:3000/oauth2/redirect?token=JWT&refreshToken=REFRESH_TOKEN
    FE->>FE: Trích xuất token từ URL, lưu vào LocalStorage/Cookie
    FE->>BE: GET /api/me (Header: Bearer JWT)
    BE-->>FE: 200 OK (User Profile JSON)
```

---

## 2. Cấu Hình Môi Trường & Google Cloud Console

### Bước 1: Tạo Google OAuth Client ID
1. Đăng nhập [Google Cloud Console](https://console.cloud.google.com/apis/credentials).
2. Tạo mới một Project (hoặc chọn Project hiện tại).
3. Vào mục **APIs & Services** $\rightarrow$ **OAuth consent screen**:
   - Chọn **External** $\rightarrow$ bấm **Create**.
   - Điền thông tin:
     - **App name**: `Memento3D`
     - **User support email**: Email của bạn.
     - **Developer contact information**: Email của bạn.
   - Nhấn **Save and Continue**.
   - Mục **Scopes**: Bấm **Add or Remove Scopes** $\rightarrow$ Chọn:
     - `.../auth/userinfo.email`
     - `.../auth/userinfo.profile`
     - `openid`
   - Bấm **Save and Continue**.
   - Mục **Test users**: Thêm các email Google bạn dự định dùng để test (khi app ở trạng thái Testing).
4. Vào mục **Credentials** $\rightarrow$ Bấm **Create Credentials** $\rightarrow$ Chọn **OAuth client ID**:
   - **Application type**: `Web application`
   - **Name**: `Memento3D Backend Client`
   - **Authorized JavaScript origins**:
     - `http://localhost:8080` (Backend API)
     - `http://localhost:3000` (Frontend Web React/Next.js)
     - `http://localhost:5173` (Frontend Web Vite nếu có)
   - **Authorized redirect URIs** (BẮT BUỘC CHÍNH XÁC):
     - `http://localhost:8080/login/oauth2/code/google`
5. Bấm **Create**, màn hình sẽ hiển thị:
   - **Client ID**: Dạng `xxxxxxxxxxxx-xxxxxxxxxxxx.apps.googleusercontent.com`
   - **Client Secret**: Dạng `GOCSPX-xxxxxxxxxxxxxxxxxxxxxxxx`

---

### Bước 2: Thiết Lập Biến Môi Trường Backend

Mở file `.env` trong thư mục `memeto3D/` (hoặc tạo từ `.env.example`) và cập nhật:

```properties
# Google OAuth2 / OpenID Connect (Project: ascendant-timer-511514-c5)
GOOGLE_CLIENT_ID=306628485438-sn1ukkn3n4getsruj6281i88fgsu5fdk.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=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
OAUTH2_REDIRECT_URI=http://localhost:3000/oauth2/redirect
```

---

## 3. Đặc Tả Endpoints Backend

### 3.1. Khởi Tạo Đăng Nhập Google
- **URL**: `GET http://localhost:8080/oauth2/authorization/google`
- **Quyền truy cập**: Public (Không cần token)
- **Mô tả**: Endpoint do Spring Security tự động cung cấp. Trình duyệt khi truy cập URL này sẽ được chuyển hướng sang giao diện đăng nhập Google.
- **Dynamic Redirect URI**: Cho phép ghi đè URL nhận kết quả sau khi đăng nhập thành công:
  ```http
  GET http://localhost:8080/oauth2/authorization/google?redirect_uri=http://localhost:3000/custom/callback
  ```

---

### 3.2. Callback Từ Google (Do Spring Security Tự Hứng)
- **URL**: `GET http://localhost:8080/login/oauth2/code/google`
- **Tham số nhận về**:
  - `code`: Authorization Code được cấp từ Google.
  - `state`: Chuỗi mã hóa CSRF được đối chiếu với Cookie của người dùng.
- **Kết quả trả về**: Chuyển hướng `302 Found` về Frontend URL kèm theo Token:
  ```http
  HTTP/1.1 302 Found
  Location: http://localhost:3000/oauth2/redirect?token=eyJhbGciOiJIUzI1NiJ9...&refreshToken=d87a412f-9876-4abc-8def-123456789abc
  ```

---

### 3.3. Lấy Thông Tin Người Dùng Đang Đăng Nhập (`/api/me`)
- **URL**: `GET /api/me` (hoặc `/api/auth/me`, `/api/v1/users/me`)
- **Quyền truy cập**: Authenticated (`Bearer <access_token>`)
- **Header**:
  ```http
  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
  ```
- **Response `200 OK`**:
  ```json
  {
    "id": "18fbe918-28fc-4fc8-999d-1981446fe318",
    "email": "kietlt.dev@gmail.com",
    "fullName": "Lê Tuấn Kiệt",
    "role": "ROLE_USER",
    "provider": "GOOGLE",
    "avatarUrl": "https://lh3.googleusercontent.com/a/ACg8ocISz..."
  }
  ```

---

### 3.4. Làm Mới Token (Refresh Token)
- **URL**: `POST /api/auth/refresh`
- **Body**:
  ```json
  {
    "refreshToken": "d87a412f-9876-4abc-8def-123456789abc"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.new_access_token...",
    "refreshToken": "e91c784a-1122-3344-5566-778899aabbcc",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "18fbe918-28fc-4fc8-999d-1981446fe318",
      "email": "kietlt.dev@gmail.com",
      "fullName": "Lê Tuấn Kiệt",
      "role": "ROLE_USER",
      "provider": "GOOGLE",
      "avatarUrl": "https://lh3.googleusercontent.com/a/ACg8ocISz..."
    }
  }
  ```

---

### 3.5. Đăng Xuất (Logout)
- **URL**: `POST /api/auth/logout`
- **Header**: `Authorization: Bearer <access_token>`
- **Body** (Optional):
  ```json
  {
    "refreshToken": "d87a412f-9876-4abc-8def-123456789abc"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "message": "Logged out successfully"
  }
  ```

---

## 4. Hướng Dẫn Kiểm Thử Từng Bước (Testing Walkthrough)

### Kịch Bản 1: Đăng Nhập Bằng Trình Duyệt Thực Tế
1. Khởi động ứng dụng backend:
   ```bash
   .\mvnw.cmd spring-boot:run
   ```
2. Mở trình duyệt (khuyến nghị chế độ Incognito/Ẩn danh) và gõ trực tiếp URL:
   ```text
   http://localhost:8080/oauth2/authorization/google
   ```
3. Bạn sẽ được chuyển hướng sang màn hình đăng nhập của Google Accounts.
4. Chọn tài khoản Google của bạn (hoặc tài khoản test đã thêm trong Google Console) và đồng ý cấp quyền.
5. Sau khi đăng nhập thành công, trình duyệt sẽ tự động chuyển hướng về:
   ```text
   http://localhost:3000/oauth2/redirect?token=eyJhbGci...&refreshToken=...
   ```
6. Copy giá trị của `token` từ thanh địa chỉ trình duyệt.

---

### Kịch Bản 2: Kiểm Thử Gọi API Bằng cURL

Dùng token vừa lấy được ở Kịch bản 1 để gọi API thông tin người dùng:

```bash
# Windows PowerShell
$TOKEN = "eyJhbGciOiJIUzI1NiJ9..."
curl.exe -X GET "http://localhost:8080/api/me" -H "Authorization: Bearer $TOKEN"
```

```bash
# Linux / macOS / Git Bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
curl -X GET "http://localhost:8080/api/me" \
     -H "Authorization: Bearer $TOKEN"
```

**Kết quả mong đợi**: Trả về `HTTP 200 OK` kèm JSON thông tin User chứa `provider: GOOGLE` và `avatarUrl`.

---

### Kịch Bản 3: Kiểm Thử Bằng Postman
1. Tạo một Request mới trong Postman:
   - **Method**: `GET`
   - **URL**: `http://localhost:8080/api/me`
2. Vào tab **Authorization**:
   - **Type**: `Bearer Token`
   - **Token**: Dán Access Token vừa nhận được.
3. Bấm **Send**: Kiểm tra kết quả trả về `Status 200 OK`.

---

### Kịch Bản 4: Kiểm Tra Cơ Chế Tự Động Liên Kết (Account Linking)
1. Dùng Postman gọi `POST /api/auth/register` để tạo 1 user bằng email thường:
   ```json
   {
     "email": "your_test_email@gmail.com",
     "password": "Password123@",
     "fullName": "Local User"
   }
   ```
2. Mở trình duyệt, truy cập `http://localhost:8080/oauth2/authorization/google` và đăng nhập bằng tài khoản Google có cùng email `your_test_email@gmail.com`.
3. Kiểm tra token nhận về: User không bị tạo trùng lặp; thông tin tài khoản được cập nhật `providerId` và `avatarUrl` từ Google mà giữ nguyên ID ban đầu.

---

## 5. Mẫu Code Tích Hợp Phía Frontend (React / SPA / HTML)

### 5.1. Nút Đăng Nhập (HTML / React Button)

```jsx
// React / Next.js Component
import React from 'react';

export const GoogleLoginButton = () => {
  const handleGoogleLogin = () => {
    // Chuyển hướng trình duyệt tới endpoint Spring Boot
    window.location.href = 'http://localhost:8080/oauth2/authorization/google';
  };

  return (
    <button
      onClick={handleGoogleLogin}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        padding: '10px 20px',
        borderRadius: '6px',
        border: '1px solid #ddd',
        backgroundColor: '#fff',
        cursor: 'pointer',
        fontWeight: 'bold',
      }}
    >
      <img
        src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg"
        alt="Google Logo"
        width="20"
        height="20"
      />
      Đăng nhập bằng Google
    </button>
  );
};
```

---

### 5.2. Trang Hứng Callback (Redirect Component)

Tạo trang tương ứng với route: `/oauth2/redirect` (ví dụ `http://localhost:3000/oauth2/redirect`):

```jsx
// React Router / Next.js Page: /oauth2/redirect
import React, { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

export const OAuth2RedirectHandler = () => {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const token = params.get('token');
    const refreshToken = params.get('refreshToken');
    const error = params.get('error');

    if (token) {
      // 1. Lưu token vào LocalStorage hoặc Cookie an toàn
      localStorage.setItem('accessToken', token);
      if (refreshToken) {
        localStorage.setItem('refreshToken', refreshToken);
      }

      // 2. Gọi API lấy thông tin người dùng
      fetch('http://localhost:8080/api/me', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
        .then((res) => res.json())
        .then((userData) => {
          console.log('Đăng nhập thành công:', userData);
          // Lưu user vào state/context và chuyển hướng về dashboard
          navigate('/dashboard');
        })
        .catch((err) => {
          console.error('Lỗi khi lấy thông tin user:', err);
          navigate('/login?error=fetch_user_failed');
        });
    } else {
      console.error('Đăng nhập thất bại:', error);
      navigate(`/login?error=${encodeURIComponent(error || 'unknown')}`);
    }
  }, [location, navigate]);

  return (
    <div style={{ textAlign: 'center', marginTop: '100px' }}>
      <h2>Đang xử lý đăng nhập Google... Vui lòng chờ...</h2>
    </div>
  );
};
```

---

## 6. Hướng Dẫn Kiểm Thử Tự Động (Automated Unit Tests)

Để chạy kiểm thử toàn bộ hệ thống xác thực và các lớp OAuth2 vừa bổ sung, chạy lệnh sau trong thư mục `memeto3D/`:

```powershell
.\mvnw.cmd test '-Dtest=SecurityConfigTest,DtoAndModelTest,CustomUserDetailsTest,CustomOAuth2UserServiceTest,OAuth2AuthenticationSuccessHandlerTest'
```

### Danh Sách Các Test Cases:
| Test Class | Nội dung kiểm thử | Trạng thái |
| :--- | :--- | :--- |
| **`SecurityConfigTest`** | Kiểm tra khởi tạo SecurityFilterChain, cấu hình PasswordEncoder BCrypt, cấu hình CORS. | Passed |
| **`CustomUserDetailsTest`** | Kiểm tra quyền `ROLE_USER`, các thuộc tính OAuth2User (`getAttributes()`, `getName()`, `getUser()`). | Passed |
| **`CustomOAuth2UserServiceTest`** | Kiểm tra trích xuất thông tin Google `sub`, `email`, `name`, `picture` và chặn provider không hợp lệ. | Passed |
| **`OAuth2AuthenticationSuccessHandlerTest`** | Kiểm tra tạo JWT, Refresh Token và tạo chuỗi Redirect URL chứa query parameters `token` & `refreshToken`. | Passed |
| **`DtoAndModelTest`** | Kiểm tra toàn bộ DTOs, entity User mới với các trường `provider`, `providerId`, `avatarUrl`. | Passed |

---

## 7. Các Cơ Chế Bảo Mật Nổi Bật

1. **Bảo Vệ Khỏi CSRF trong OAuth2 (`state` parameter)**:
   - Request bắt đầu đăng nhập sinh ra một mã nonce ngẫu nhiên (`state`), được mã hóa lưu trong HttpOnly Cookie `oauth2_auth_request`.
   - Khi Google callback về, Spring Security đối chiếu `state` trong request URL với `state` trong Cookie. Nếu không khớp sẽ từ chối ngay lập tức.
2. **Khắc Phục Lỗi Mất State Trên Kiến Trúc Stateless**:
   - Ứng dụng không cần kích hoạt session server (`SessionCreationPolicy.STATELESS`), giúp backend có thể scale ngang (horizontal scaling) ra nhiều container/server mà không gặp lỗi `authorization_request_not_found`.
3. **Bảo Vệ Bí Mật API (Credentials Safety)**:
   - `client-id` và `client-secret` được inject qua biến môi trường thông qua cú pháp `${GOOGLE_CLIENT_ID}` trong `application.yml`, ngăn ngừa rủi ro commit secret keys lên Git repository.
4. **Hạn Chế Lộ Token Trong Browser History**:
   - Phía Frontend sau khi nhận token qua URL redirect cần ngay lập tức trích xuất và chuyển trang bằng `navigate('/dashboard')` để xóa bỏ query param chứa token khỏi lịch sử trình duyệt.

---

## 8. Troubleshooting & Các Lỗi Thường Gặp

### Lỗi 1: `redirect_uri_mismatch` (Lỗi từ Google)
- **Biểu hiện**: Màn hình Google báo lỗi *"Access blocked: authorization error - Error 400: redirect_uri_mismatch"*.
- **Nguyên nhân**: URL callback gửi từ backend không trùng khớp từng ký tự với URI đã khai báo trên Google Cloud Console.
- **Cách khắc phục**:
  1. Vào Google Cloud Console $\rightarrow$ **Credentials** $\rightarrow$ chọn Client ID.
  2. Tại mục **Authorized redirect URIs**, đảm bảo có chính xác dòng sau:
     ```text
     http://localhost:8080/login/oauth2/code/google
     ```
  3. Lưu ý: Không dùng `127.0.0.1`, không thừa dấu gạch chéo `/` ở cuối.

---

### Lỗi 2: `authorization_request_not_found`
- **Biểu hiện**: Sau khi chọn tài khoản Google, chuyển về backend bị lỗi `authorization_request_not_found`.
- **Nguyên nhân**: Cookie lưu `oauth2_auth_request` bị mất hoặc quá hạn (> 180s) hoặc trình duyệt chặn cookie bên thứ 3.
- **Cách khắc phục**:
  1. Đảm bảo [HttpCookieOAuth2AuthorizationRequestRepository.java](file:///c:/Users/KIETLT/Documents/GitHub/Memento3d/Memento3D-Backend/memeto3D/src/main/java/com/memento3d/identity/infrastructure/security/oauth2/HttpCookieOAuth2AuthorizationRequestRepository.java) đã được cấu hình trong `SecurityConfig`.
  2. Nếu chạy localhost, hãy đảm bảo cookie `SameSite` và `Path=/` được thiết lập đúng.

---

### Lỗi 3: `403 Forbidden` khi truy cập `/oauth2/authorization/google`
- **Biểu hiện**: Bấm nút đăng nhập nhưng nhận về HTTP 403.
- **Nguyên nhân**: Endpoint chưa được phân quyền `permitAll()`.
- **Cách khắc phục**: Kiểm tra [SecurityConfig.java](file:///c:/Users/KIETLT/Documents/GitHub/Memento3d/Memento3D-Backend/memeto3D/src/main/java/com/memento3d/shared/security/SecurityConfig.java), đảm bảo có dòng:
  ```java
  .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
  ```

---

### Lỗi 4: Không thể kết nối Database khi chạy Flyway Migration V6
- **Biểu hiện**: Ứng dụng không khởi động được do lỗi `FlywayException` hoặc lỗi validate schema Hibernate.
- **Cách khắc phục**:
  - Đảm bảo cơ sở dữ liệu PostgreSQL đang chạy.
  - File migration [V6__add_oauth2_fields_to_users.sql](file:///c:/Users/KIETLT/Documents/GitHub/Memento3d/Memento3D-Backend/memeto3D/src/main/resources/db/migration/V6__add_oauth2_fields_to_users.sql) sẽ tự động chạy khi khởi động app và bổ sung các trường cần thiết vào bảng `users`.

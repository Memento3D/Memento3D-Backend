# MEMENTO3D — FRONTEND ARCHITECTURE & DEVELOPER GUIDE

## 1. Overview & Tech Stack
Dự án Frontend Web của Memento3D (`Memento3D-Fronted-Web`) được phát triển dưới dạng **Single Page Application (SPA)** hiện đại:

- **Core**: React 18+, TypeScript 5+
- **Build Tool**: Vite
- **UI Component System**: Ant Design 5.x (`antd`), `@ant-design/icons`
- **State Management**: React Context API (`AuthContext`, `WarehouseContext`), React Query / Custom Hooks
- **Routing & Guards**: React Router DOM v6
- **HTTP Client**: Axios với Interceptors tự động đính kèm JWT Bearer và refresh token khi gặp 401
- **3D Visualization**: Three.js, `@react-three/fiber`, `@react-three/drei` (Viewer hiển thị tệp `.glb`/`.obj`)

---

## 2. Directory Structure

```text
Memento3D-Fronted-Web/
├── public/                 # Static assets (favicons, 3D samples, logos)
├── src/
│   ├── assets/             # Images, SVGs, global brand icons
│   ├── components/         # Reusable presentation components
│   │   ├── common/         # Buttons, Badges, Loaders, EmptyStates
│   │   ├── layout/         # AppLayout, Navbar, Sidebar, Footer
│   │   └── viewer3d/       # Three.js Canvas Viewer with OrbitControls
│   ├── features/           # Feature Modules (Domain-Driven)
│   │   ├── auth/           # Login, Register, Google OAuth, Profile
│   │   │   ├── api/        # authApi.ts
│   │   │   ├── context/    # AuthContext.tsx
│   │   │   └── pages/      # LoginPage.tsx, RegisterPage.tsx
│   │   ├── scans/          # Photo upload, scan preview, QC reviewer
│   │   ├── orders/         # Order creation, order tracking, invoice
│   │   ├── printers/       # Printer farm dashboard, telemetry widgets
│   │   ├── warehouse/      # WMS: Materials, Inventory, Receipts, Issues, Ledger
│   │   │   ├── api/        # warehouseApi.ts
│   │   │   ├── context/    # WarehouseContext.tsx
│   │   │   └── pages/      # WarehouseDashboardPage.tsx
│   │   ├── provider/       # Provider Portal for partners
│   │   └── driver/         # Driver Delivery Portal
│   ├── routes/             # AppRoutes.tsx, AuthGuard.tsx, RoleGuard.tsx
│   ├── services/           # apiClient.ts (Axios instance with Interceptors)
│   ├── types/              # Global TypeScript interfaces & DTO definitions
│   ├── App.tsx             # Root Application Component
│   ├── index.css           # Global typography, color tokens, scrollbars
│   └── main.tsx            # Application entrypoint with Antd ConfigProvider
├── package.json
└── vite.config.ts
```

---

## 3. Route & Role Matrix

| Route Path | Allowed Roles | Page Component | Associated Backend APIs |
|---|---|---|---|
| `/` | **PUBLIC** | `HomePage.tsx` | N/A |
| `/login` | **PUBLIC** | `LoginPage.tsx` | `POST /api/v1/auth/login`, `POST /api/v1/auth/oauth2/google` |
| `/register` | **PUBLIC** | `RegisterPage.tsx` | `POST /api/v1/auth/register` |
| `/customer/scans` | `ROLE_CUSTOMER`, `ROLE_ADMIN` | `CustomerScansPage.tsx` | `GET /api/v1/scans/my`, `POST /api/v1/scans/upload` |
| `/customer/scans/:id` | Owner, `ROLE_ADMIN` | `ScanViewer3DPage.tsx` | `GET /api/v1/scans/{id}`, `GET /api/v1/models/scan/{id}` |
| `/customer/orders` | `ROLE_CUSTOMER`, `ROLE_ADMIN` | `CustomerOrdersPage.tsx`| `GET /api/v1/orders`, `POST /api/v1/orders` |
| `/customer/checkout/:orderId` | `ROLE_CUSTOMER` | `CheckoutPage.tsx` | `POST /api/v1/payments/create` |
| `/operator/qc` | `ROLE_OPERATOR`, `ROLE_MANAGER`, `ROLE_ADMIN` | `QcQueuePage.tsx` | `GET /api/v1/scans/qc/queue`, `POST /api/v1/scans/qc/{id}/approve` |
| `/operator/printers` | `ROLE_OPERATOR`, `ROLE_MANAGER`, `ROLE_ADMIN` | `PrintersPage.tsx` | `GET /api/v1/printers`, `POST /api/v1/print-jobs/{id}/start` |
| `/wms` | `ROLE_WAREHOUSE`, `ROLE_OPERATOR`, `ROLE_MANAGER`, `ROLE_ADMIN` | `WarehouseDashboardPage.tsx` | `GET /api/v1/wms/**` |
| `/provider/dashboard` | `ROLE_PROVIDER`, `ROLE_ADMIN` | `ProviderDashboardPage.tsx` | `GET /api/v1/orders?partner=me` |
| `/driver/deliveries` | `ROLE_DRIVER`, `ROLE_ADMIN` | `DriverDeliveriesPage.tsx` | `GET /api/v1/orders?status=READY_FOR_SHIPPING` |
| `/admin/users` | `ROLE_ADMIN` | `AdminUserManagementPage.tsx` | `GET /api/v1/admin/users` |

---

## 4. Axios Interceptors & Token Handling

Tại `src/services/apiClient.ts`:
```typescript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request Interceptor: Attach Access Token
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response Interceptor: Auto Handle 401 & Refresh Token
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const refreshToken = localStorage.getItem('refresh_token');
        if (!refreshToken) throw new Error('No refresh token');
        
        const res = await axios.post('/api/v1/auth/refresh', { refreshToken });
        const { accessToken } = res.data.data;
        localStorage.setItem('auth_token', accessToken);
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(originalRequest);
      } catch (refreshErr) {
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(refreshErr);
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

---

## 5. UI/UX & Responsive Guidelines
- Sử dụng Ant Design Token Theme (`ConfigProvider`) với tông màu chủ đạo:
  - **Primary**: Xanh ngọc công nghệ cao (`#06b6d4` / `#0891b2`)
  - **Background**: Dark mode hiện đại (`#0f172a` / `#1e293b`) hoặc Light mode trang nhã (`#f8fafc`).
- Mọi thao tác Mutation (Thêm, Sửa, Xóa, Nhập/Xuất kho) bắt buộc hiển thị:
  - Loading State trên Button/Table.
  - Notification Toast (`notification.success`, `notification.error`).
  - Modal xác nhận đối với hành vi có rủi ro cao (Hủy đơn, Xóa máy in).

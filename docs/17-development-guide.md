# MEMENTO3D — DEVELOPER GUIDE: HOW TO ADD A NEW FEATURE

## 1. Overview
Tài liệu này cung cấp hướng dẫn mẫu chuẩn mực (Standard Recipe) để một kỹ sư phần mềm bổ sung một tính năng mới vào Memento3D, từ cơ sở dữ liệu, API Backend tới giao diện Frontend Web.

Ví dụ minh họa: **Bổ sung tính năng Quản lý Nhà cung cấp Vật liệu (Material Supplier Management)** vào module Kho (WMS).

---

## 2. Backend Implementation (10 Bước)

### Bước 1: Tạo Database Migration (Flyway)
Tạo file `V7__create_material_suppliers_table.sql` trong `src/main/resources/db/migration/`:
```sql
CREATE TABLE IF NOT EXISTS material_suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    address TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### Bước 2: Tạo JPA Entity
Tạo `SupplierEntity.java` trong `com.memento3d.material.domain.model`:
```java
@Entity
@Table(name = "material_suppliers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SupplierEntity extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    private String contactName;
    private String phone;
    private String email;
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SupplierStatus status = SupplierStatus.ACTIVE;
}
```

### Bước 3: Tạo Spring Data Repository
```java
@Repository
public interface SupplierRepository extends JpaRepository<SupplierEntity, UUID> {
    Optional<SupplierEntity> findByCode(String code);
    boolean existsByCode(String code);
    Page<SupplierEntity> findAllByStatus(SupplierStatus status, Pageable pageable);
}
```

### Bước 4: Tạo Request & Response DTOs
```java
public record CreateSupplierRequest(
    @NotBlank(message = "Supplier code is required")
    @Size(max = 50)
    String code,

    @NotBlank(message = "Supplier name is required")
    @Size(max = 255)
    String name,

    String contactName,
    String phone,
    @Email String email,
    String address
) {}

public record SupplierResponse(
    UUID id,
    String code,
    String name,
    String contactName,
    String phone,
    String email,
    String address,
    SupplierStatus status,
    Instant createdAt
) {}
```

### Bước 5: Tạo MapStruct Mapper
```java
@Mapper(componentModel = "spring")
public interface SupplierMapper {
    SupplierEntity toEntity(CreateSupplierRequest request);
    SupplierResponse toResponse(SupplierEntity entity);
    List<SupplierResponse> toResponseList(List<SupplierEntity> entities);
}
```

### Bước 6: Xây dựng Service & Business Logic
```java
public interface SupplierService {
    SupplierResponse createSupplier(CreateSupplierRequest request);
    Page<SupplierResponse> getSuppliers(SupplierStatus status, Pageable pageable);
    SupplierResponse getSupplierById(UUID id);
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {
    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    @Override
    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        if (supplierRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Supplier code already exists: " + request.code());
        }
        SupplierEntity entity = supplierMapper.toEntity(request);
        SupplierEntity saved = supplierRepository.save(entity);
        return supplierMapper.toResponse(saved);
    }
}
```

### Bước 7: Xây dựng REST Controller
```java
@RestController
@RequestMapping("/api/v1/wms/suppliers")
@RequiredArgsConstructor
@Tag(name = "WMS Suppliers", description = "Quản lý nhà cung cấp vật liệu")
public class SupplierController {
    private final SupplierService supplierService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Tạo nhà cung cấp mới")
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody CreateSupplierRequest request) {
        SupplierResponse response = supplierService.createSupplier(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo nhà cung cấp thành công", response));
    }
}
```

---

## 3. Frontend Implementation (4 Bước)

### Bước 1: Định nghĩa TypeScript Interfaces
Tại `src/types/supplier.ts`:
```typescript
export interface Supplier {
  id: string;
  code: string;
  name: string;
  contactName?: string;
  phone?: string;
  email?: string;
  address?: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
}

export interface CreateSupplierInput {
  code: string;
  name: string;
  contactName?: string;
  phone?: string;
  email?: string;
  address?: string;
}
```

### Bước 2: Tạo API Service Client
Tại `src/features/warehouse/api/supplierApi.ts`:
```typescript
import apiClient from '../../../services/apiClient';
import { Supplier, CreateSupplierInput } from '../../../types/supplier';

export const supplierApi = {
  getSuppliers: () => apiClient.get<ApiResponse<Supplier[]>>('/wms/suppliers'),
  createSupplier: (data: CreateSupplierInput) => 
    apiClient.post<ApiResponse<Supplier>>('/wms/suppliers', data),
};
```

### Bước 3: Tạo UI Component / Page
Tại `src/features/warehouse/pages/SupplierListPage.tsx`:
- Sử dụng Ant Design `Table`, `Button`, `Modal`, `Form`, và `notification.success`.
- Đảm bảo có loading state khi fetching và submitting.

### Bước 4: Đăng ký Route & Role Guard
Thêm vào `src/routes/AppRoutes.tsx`:
```tsx
<Route
  path="/wms/suppliers"
  element={
    <RoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_MANAGER']}>
      <SupplierListPage />
    </RoleGuard>
  }
/>
```

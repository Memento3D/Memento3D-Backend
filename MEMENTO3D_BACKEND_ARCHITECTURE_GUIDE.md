# Memento3D Backend — Source Architecture Guide

> Đây là tài liệu chuẩn để Backend member tổ chức source code. Tài liệu chỉ quy định kiến trúc source, dependency và vị trí đặt code; không chứa kế hoạch triển khai, hạ tầng hoặc nội dung vận hành.

## 1. Kiến trúc thống nhất

Backend sử dụng **package-by-domain** kết hợp **pragmatic domain layer**.

Mỗi domain có tối đa bốn package chính:

```text
<domain>/
├── api/
├── application/
├── domain/
└── infrastructure/
```

Không tổ chức toàn dự án theo các package toàn cục như sau:

```text
controller/
service/
repository/
entity/
```

Mục tiêu là giữ toàn bộ code của một nghiệp vụ ở gần nhau. Khi sửa `identity`, member chỉ cần làm việc chủ yếu trong package `identity`.

## 2. Cấu trúc source chuẩn

```text
Memento3D-Backend/
└── memeto3D/
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/
        │   ├── java/com/memento3d/
        │   │   ├── Memento3DApplication.java
        │   │   ├── shared/
        │   │   │   ├── api/
        │   │   │   │   ├── ApiExceptionHandler.java
        │   │   │   │   └── response/ApiErrorResponse.java
        │   │   │   ├── security/
        │   │   │   │   ├── SecurityConfiguration.java
        │   │   │   │   └── JwtAuthenticationFilter.java
        │   │   │   ├── config/
        │   │   │   │   ├── RedisConfiguration.java
        │   │   │   │   └── OpenApiConfiguration.java
        │   │   │   └── observability/RequestIdFilter.java
        │   │   ├── identity/
        │   │   │   ├── api/
        │   │   │   │   ├── AuthController.java
        │   │   │   │   ├── UserController.java
        │   │   │   │   ├── request/
        │   │   │   │   │   ├── LoginRequest.java
        │   │   │   │   │   ├── RegisterUserRequest.java
        │   │   │   │   │   └── RefreshTokenRequest.java
        │   │   │   │   └── response/
        │   │   │   │       ├── AuthResponse.java
        │   │   │   │       └── UserResponse.java
        │   │   │   ├── application/
        │   │   │   │   ├── dto/
        │   │   │   │   │   ├── AuthResult.java
        │   │   │   │   │   ├── UserSummary.java
        │   │   │   │   │   ├── LoginCommand.java
        │   │   │   │   │   ├── RefreshTokenCommand.java
        │   │   │   │   │   └── LogoutCommand.java
        │   │   │   │   └── service/
        │   │   │   │       ├── AuthService.java
        │   │   │   │       ├── UserService.java
        │   │   │   │       └── impl/
        │   │   │   │           ├── AuthServiceImpl.java
        │   │   │   │           └── UserServiceImpl.java
        │   │   │   ├── domain/
        │   │   │   │   └── model/
        │   │   │   │       ├── User.java
        │   │   │   │       └── UserRole.java
        │   │   │   └── infrastructure/
        │   │   │       ├── persistence/repository/UserRepository.java
        │   │   │       └── security/JwtTokenService.java
        │   │   ├── scans/
        │   │   │   ├── api/
        │   │   │   │   ├── ScanController.java
        │   │   │   │   ├── request/
        │   │   │   │   │   ├── CreateScanRequest.java
        │   │   │   │   │   └── UpdateScanRequest.java
        │   │   │   │   └── response/ScanResponse.java
        │   │   │   ├── application/
        │   │   │   │   ├── dto/ScanSummary.java
        │   │   │   │   └── service/
        │   │   │   │       ├── ScanService.java
        │   │   │   │       └── impl/ScanServiceImpl.java
        │   │   │   ├── domain/model/
        │   │   │   │   ├── ScanProject.java
        │   │   │   │   └── ScanStatus.java
        │   │   │   └── infrastructure/persistence/repository/
        │   │   │       └── ScanRepository.java
        │   │   ├── assets/
        │   │   │   ├── api/
        │   │   │   │   ├── AssetController.java
        │   │   │   │   ├── request/CreateUploadRequest.java
        │   │   │   │   └── response/UploadSessionResponse.java
        │   │   │   ├── application/
        │   │   │   │   ├── dto/UploadResult.java
        │   │   │   │   └── service/
        │   │   │   │       ├── AssetService.java
        │   │   │   │       └── impl/AssetServiceImpl.java
        │   │   │   ├── domain/
        │   │   │   │   ├── model/
        │   │   │   │   │   ├── Asset.java
        │   │   │   │   │   └── UploadSession.java
        │   │   │   │   └── gateway/ObjectStorage.java
        │   │   │   └── infrastructure/
        │   │   │       ├── persistence/repository/AssetRepository.java
        │   │   │       └── storage/S3ObjectStorage.java
        │   │   ├── quality/
        │   │   │   ├── api/
        │   │   │   │   ├── QualityController.java
        │   │   │   │   ├── request/RecordQualityRequest.java
        │   │   │   │   └── response/QualityReportResponse.java
        │   │   │   ├── application/
        │   │   │   │   ├── dto/QualityResult.java
        │   │   │   │   └── service/
        │   │   │   │       ├── QualityService.java
        │   │   │   │       └── impl/QualityServiceImpl.java
        │   │   │   ├── domain/model/QualityReport.java
        │   │   │   └── infrastructure/persistence/repository/
        │   │   │       └── QualityRepository.java
        │   │   ├── reconstruction/
        │   │   │   ├── api/
        │   │   │   │   ├── ReconstructionController.java
        │   │   │   │   ├── WorkerController.java
        │   │   │   │   ├── request/
        │   │   │   │   │   ├── CreateJobRequest.java
        │   │   │   │   │   ├── JobProgressRequest.java
        │   │   │   │   │   └── CompleteJobRequest.java
        │   │   │   │   └── response/
        │   │   │   │       ├── ReconstructionJobResponse.java
        │   │   │   │       └── WorkerClaimResponse.java
        │   │   │   ├── application/
        │   │   │   │   ├── dto/
        │   │   │   │   │   ├── JobSummary.java
        │   │   │   │   │   └── ExecutionManifest.java
        │   │   │   │   ├── scheduler/JobReconciliationScheduler.java
        │   │   │   │   └── service/
        │   │   │   │       ├── ReconstructionService.java
        │   │   │   │       ├── WorkerJobService.java
        │   │   │   │       └── impl/
        │   │   │   │           ├── ReconstructionServiceImpl.java
        │   │   │   │           └── WorkerJobServiceImpl.java
        │   │   │   ├── domain/
        │   │   │   │   ├── model/
        │   │   │   │   │   ├── ReconstructionJob.java
        │   │   │   │   │   ├── JobAttempt.java
        │   │   │   │   │   └── JobStatus.java
        │   │   │   │   ├── service/
        │   │   │   │   │   ├── JobStateMachine.java
        │   │   │   │   │   └── impl/JobStateMachineImpl.java
        │   │   │   │   └── gateway/JobQueue.java
        │   │   │   └── infrastructure/
        │   │   │       ├── persistence/repository/
        │   │   │       │   ├── ReconstructionRepository.java
        │   │   │       │   └── JobAttemptRepository.java
        │   │   │       ├── queue/RedisJobQueuePublisher.java
        │   │   │       └── outbox/OutboxPublisher.java
        │   │   ├── models/
        │   │   │   ├── api/
        │   │   │   │   ├── ModelController.java
        │   │   │   │   └── response/ModelResponse.java
        │   │   │   ├── application/
        │   │   │   │   ├── dto/ModelSummary.java
        │   │   │   │   └── service/
        │   │   │   │       ├── ModelService.java
        │   │   │   │       └── impl/ModelServiceImpl.java
        │   │   │   ├── domain/model/GeneratedModel.java
        │   │   │   └── infrastructure/persistence/repository/
        │   │   │       └── ModelRepository.java
        │   │   ├── quota/
        │   │   │   ├── application/service/
        │   │   │   │   ├── QuotaService.java
        │   │   │   │   └── impl/QuotaServiceImpl.java
        │   │   │   ├── domain/model/UsageLedger.java
        │   │   │   └── infrastructure/persistence/repository/
        │   │   │       └── QuotaRepository.java
        │   │   ├── sharing/
        │   │   │   ├── api/
        │   │   │   │   ├── SharingController.java
        │   │   │   │   ├── request/CreateShareLinkRequest.java
        │   │   │   │   └── response/ShareLinkResponse.java
        │   │   │   ├── application/
        │   │   │   │   ├── dto/ShareLinkResult.java
        │   │   │   │   └── service/
        │   │   │   │       ├── SharingService.java
        │   │   │   │       └── impl/SharingServiceImpl.java
        │   │   │   ├── domain/model/ShareLink.java
        │   │   │   └── infrastructure/persistence/repository/
        │   │   │       └── SharingRepository.java
        │   │   └── operations/
        │   │       ├── api/
        │   │       │   ├── OperationsController.java
        │   │       │   └── response/SystemHealthResponse.java
        │   │       └── application/
        │   │           ├── dto/SystemHealthResult.java
        │   │           └── service/
        │   │               ├── OperationsService.java
        │   │               └── impl/OperationsServiceImpl.java
        │   └── resources/
        │       ├── application.yml
        │       ├── application-local.yml
        │       ├── application-test.yml
        │       ├── application-staging.yml
        │       ├── application-production.yml
        │       └── db/migration/
        └── test/java/com/memento3d/
            ├── identity/
            ├── scans/
            ├── assets/
            ├── quality/
            ├── reconstruction/
            ├── models/
            └── support/
```

## 3. Trách nhiệm từng layer

### `api`

- Chứa controller và HTTP contract.
- `request` chỉ dùng cho dữ liệu client gửi vào.
- `response` chỉ dùng cho dữ liệu trả về client.
- Thực hiện HTTP validation và mapping; không viết business rule.
- Không trả JPA/domain model trực tiếp.

### `application`

- `service` chứa interface mô tả application contract.
- `service/impl` chứa implementation điều phối business flow.
- Implementation quản lý transaction boundary bằng `@Transactional`.
- Gọi repository, domain method và external gateway.
- `dto` chứa command/result nội bộ; không chứa annotation HTTP hoặc JPA.
- Không xử lý trực tiếp `HttpServletRequest`, status code hoặc JSON.

### `domain`

- `model` chứa entity, aggregate, value object và enum nghiệp vụ.
- `service` chỉ dành cho business rule phối hợp nhiều model.
- `gateway` là interface cho external capability cần thay thế implementation.
- Domain không import controller, request, response hoặc application service.
- Domain entity đồng thời đảm nhiệm ánh xạ JPA theo kiến trúc hiện tại.

### `infrastructure`

- `persistence/repository` chứa Spring Data repository.
- `security` chứa JWT, password encoder và security adapter theo domain.
- `storage` chứa S3/Cloudinary implementation.
- `queue` chứa producer/consumer kỹ thuật.
- `outbox` chứa publisher và persistence của outbox.
- Không đặt business rule trong infrastructure.

## 4. Quy tắc dependency

```text
api → application
application → domain + infrastructure repository/gateway
infrastructure → domain model
domain → không phụ thuộc api hoặc application
```

Không được có dependency vòng:

```text
domain → application        # cấm
domain → api                # cấm
infrastructure → api        # cấm
repository → controller     # cấm
```

Một domain không truy cập trực tiếp repository của domain khác. Nếu `reconstruction` cần thông tin từ `quota`, nó gọi `QuotaService` hoặc một facade public của module `quota`.

## 5. Quy tắc model và persistence

Trong kiến trúc hiện tại, một class có thể đồng thời là domain model và JPA entity:

```java
@Entity
@Table(name = "users")
public class User {
    // state + domain behavior
}
```

Repository làm việc trực tiếp với model đó:

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
```

Quy ước persistence của source hiện tại:

- Domain model chịu trách nhiệm ánh xạ JPA.
- Mỗi aggregate có Spring Data repository tương ứng trong `infrastructure/persistence/repository`.
- Repository query trả về domain model.
- Application service quản lý transaction và thay đổi trạng thái model.
- Controller chuyển kết quả sang `api/response`, không trả model trực tiếp.
- Mọi thay đổi schema đi qua migration trong `resources/db/migration`.

## 6. Quy tắc interface

Interface dùng để định nghĩa **contract ổn định giữa hai phần code**, không phải để tạo thêm file cho mọi class.

### 6.1. Interface nào cần có?

| Loại | Có cần interface? | Vị trí | Ví dụ |
|---|---|---|---|
| Spring Data repository | Có | `infrastructure/persistence/repository` | `UserRepository` |
| External gateway mà application phụ thuộc | Có | `domain/gateway` | `ObjectStorage`, `JobQueue` |
| Application service | Có | `application/service` | `AuthService` + `AuthServiceImpl` |
| Domain service | Có | `domain/service` | `JobStateMachine` + `JobStateMachineImpl` |
| Controller | Không | `api` | `AuthController` class |
| Request/response/DTO/model | Không | Package tương ứng | Data class/record bình thường |
| Mapper | Không | Nơi sử dụng | Method private hoặc concrete mapper |

### 6.2. Repository interface

Repository là interface vì Spring Data tự tạo implementation lúc runtime:

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Vị trí:

```text
identity/
└── infrastructure/
    └── persistence/
        └── repository/
            └── UserRepository.java
```

Spring Data cung cấp implementation lúc runtime. Team chỉ khai báo repository contract và các query cần thiết bằng derived query, JPQL hoặc specification.

### 6.3. External gateway interface

Khi application cần một capability bên ngoài như object storage hoặc queue, định nghĩa interface theo ngôn ngữ nghiệp vụ:

```java
public interface ObjectStorage {
    UploadLocation createUploadLocation(AssetKey key, Duration ttl);
    URI createDownloadUrl(AssetKey key, Duration ttl);
    void delete(AssetKey key);
}
```

Implementation nằm trong infrastructure và mang tên công nghệ cụ thể:

```java
@Component
public class S3ObjectStorage implements ObjectStorage {
    // AWS S3 SDK implementation
}
```

Cấu trúc:

```text
assets/
├── domain/gateway/ObjectStorage.java
└── infrastructure/storage/S3ObjectStorage.java

reconstruction/
├── domain/gateway/JobQueue.java
└── infrastructure/queue/RedisJobQueuePublisher.java
```

Application chỉ inject interface:

```java
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {
    private final AssetRepository assetRepository;
    private final ObjectStorage objectStorage;
}
```

### 6.4. Application service luôn có interface và implementation

Mọi application service dùng một interface và một implementation:

```text
application/
└── service/
    ├── AuthService.java
    └── impl/
        └── AuthServiceImpl.java
```

Interface chỉ khai báo contract:

```java
public interface AuthService {
    AuthResult login(LoginCommand command);
    AuthResult refreshToken(RefreshTokenCommand command);
    void logout(LogoutCommand command);
}
```

Implementation chứa orchestration và transaction:

```java
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;

    @Override
    public AuthResult login(LoginCommand command) {
        // application flow
    }
}
```

Controller inject interface, không inject implementation:

```java
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
}
```

Domain service cũng theo quy tắc tương tự:

```text
domain/
└── service/
    ├── JobStateMachine.java
    └── impl/JobStateMachineImpl.java
```

Interface và implementation phải nằm trong cùng domain. Không đưa service interface vào `shared`.

### 6.5. Quy tắc đặt tên interface

- Không dùng tiền tố `I`: dùng `ObjectStorage`, không dùng `IObjectStorage`.
- Interface đặt tên theo capability: `JobQueue`, `TokenIssuer`, `Clock`.
- Implementation đặt tên theo công nghệ: `S3ObjectStorage`, `RedisJobQueuePublisher`, `JwtTokenService`.
- Application/domain service implementation dùng hậu tố `Impl`: `AuthServiceImpl`, `JobStateMachineImpl`.
- External gateway implementation dùng tên công nghệ thay vì `Impl`: `S3ObjectStorage`, không dùng `ObjectStorageImpl`.
- Repository Spring Data giữ hậu tố `Repository`: `UserRepository`, `ScanRepository`.

### 6.6. Khi nào không tạo interface?

Không tạo interface chỉ vì:

- Muốn mock class trong unit test; Mockito có thể mock concrete class.
- Nghĩ rằng “sau này có thể có implementation khác” nhưng hiện chưa có nhu cầu.
- Muốn mọi class có cấu trúc giống nhau.
- Class chỉ là DTO, mapper, controller, scheduler, configuration hoặc domain model.

Ngoại lệ theo convention của dự án: application service và domain service vẫn luôn có interface + `Impl` để toàn bộ Backend thống nhất style Java/Spring.

Quy tắc quyết định:

```text
Spring Data repository?                  → interface
External capability cần cô lập?          → interface
Application/domain service?              → interface + Impl
Controller/DTO/entity/mapper/scheduler?   → concrete class
```

## 7. Quy tắc request, response và DTO

Ba loại dữ liệu không dùng thay nhau:

```text
api/request     → HTTP input + validation
application/dto → application command/result
api/response    → HTTP output
```

Flow chuẩn:

```text
LoginRequest
    → AuthService
        → AuthResult
            → AuthResponse
```

Quy tắc:

- Request không đi xuống repository.
- Response không được dùng làm cache/database entity.
- Application DTO không chứa `@JsonProperty`, `@Entity` hoặc validation dành riêng cho HTTP.
- Password, token secret và internal metadata không xuất hiện trong response.

## 8. Quy tắc service

- Mỗi application/domain service phải có interface và implementation trong package `impl`.
- Controller và module khác chỉ inject service interface.
- Chỉ implementation được gắn `@Service`, `@Transactional` và chứa dependency injection.
- Mặc định một service contract cho một nhóm nghiệp vụ liên quan; không tạo interface quá nhỏ cho từng method.
- Tên method dùng động từ nghiệp vụ: `register`, `createScan`, `completeUpload`, `claimJob`.
- Public method là transaction boundary; helper method để `private`.
- Không gọi HTTP, S3 hoặc queue trực tiếp từ controller.
- Không truy vấn database bên trong vòng lặp nếu có thể batch query.
- Không bắt `Exception` chung rồi bỏ qua lỗi.

Ví dụ:

```text
AuthController → AuthService → AuthServiceImpl → UserRepository
AssetController → AssetService → AssetServiceImpl → AssetRepository + ObjectStorage
WorkerController → WorkerJobService → WorkerJobServiceImpl → ReconstructionRepository + OutboxPublisher
```

## 9. Quy tắc repository

- Repository chỉ chứa query và persistence operation.
- Không đặt business decision trong default method hoặc custom query implementation.
- Tên query phải nói rõ điều kiện: `findByEmail`, `findByStatusAndLeaseUntilBefore`.
- Luôn giới hạn hoặc phân trang collection có thể tăng không giới hạn.
- Repository chỉ thuộc một domain; không tạo `CommonRepository` hoặc generic business repository.
- Entity lifecycle update phải đi qua application service có transaction.

## 10. Quy tắc shared

Chỉ đặt code vào `shared` khi ít nhất hai domain thực sự sử dụng và code không thuộc nghiệp vụ riêng nào.

Được đặt trong `shared`:

- API error contract.
- Security/filter nền tảng.
- Framework configuration.
- Correlation/request ID.
- Clock hoặc utility kỹ thuật thực sự dùng chung.

Không đặt trong `shared`:

- `User`, `ScanProject`, `ReconstructionJob`.
- Business service.
- Repository của một domain.
- DTO chỉ dùng cho một endpoint.
- Thư mục `utils` chứa code không rõ owner.

## 11. Cách thêm một feature mới

Ví dụ thêm feature `collections`:

```text
collections/
├── api/
│   ├── CollectionController.java
│   ├── request/CreateCollectionRequest.java
│   └── response/CollectionResponse.java
├── application/
│   ├── dto/CollectionSummary.java
│   └── service/
│       ├── CollectionService.java
│       └── impl/CollectionServiceImpl.java
├── domain/model/Collection.java
└── infrastructure/persistence/repository/CollectionRepository.java
```

Thứ tự triển khai:

1. Xác định domain owner và model.
2. Viết repository query tối thiểu.
3. Viết application service và transaction.
4. Viết request/response contract.
5. Viết controller mapping.
6. Thêm migration.
7. Thêm domain/service test và API integration test.

Không tạo package hoặc interface rỗng để “dành cho tương lai”.

## 12. Quy tắc test theo source

Test mirror package của production:

```text
src/test/java/com/memento3d/
└── identity/
    ├── application/service/impl/AuthServiceImplTest.java
    ├── api/AuthControllerIT.java
    └── infrastructure/UserRepositoryIT.java
```

- Domain rule: unit test không cần Spring context.
- Application service: mock external dependency, kiểm business flow.
- Repository: integration test với PostgreSQL/Testcontainers.
- Controller: kiểm validation, authorization và response contract.
- Không test private method trực tiếp.

## 13. Checklist pull request

- [ ] Code nằm đúng domain owner.
- [ ] Controller không chứa business rule.
- [ ] Request, response và application DTO được tách đúng.
- [ ] Domain model không phụ thuộc API/application.
- [ ] Repository không chứa business decision.
- [ ] Application/domain service có interface và implementation trong package `impl`.
- [ ] Controller inject service interface, không inject trực tiếp `*ServiceImpl`.
- [ ] Repository và external gateway có interface đúng package.
- [ ] Không trả entity trực tiếp từ controller.
- [ ] Persistence tuân thủ đúng một domain model và một Spring Data repository cho mỗi aggregate.
- [ ] Không có dependency vòng hoặc domain truy cập chéo repository.
- [ ] Transaction nằm ở application service.
- [ ] Migration và test đi cùng thay đổi persistence.
- [ ] Không đưa business code riêng vào `shared`.

## 14. Những điều cần tránh

- Không quay lại cấu trúc package toàn cục `controller/service/repository/entity`.
- Không đặt business rule trong controller, repository hoặc infrastructure adapter.
- Không dùng request làm application DTO hoặc dùng domain model làm response.
- Không trả entity trực tiếp từ API.
- Không để controller inject trực tiếp class trong package `service/impl`.
- Không để một domain truy cập repository của domain khác.
- Không đưa business code của một domain vào `shared`.
- Không tạo dependency vòng giữa các package hoặc domain.
- Không mở transaction trong controller.
- Không gọi S3, queue hoặc external API trực tiếp từ controller.
- Không tạo `CommonService`, `BaseBusinessService` hoặc generic abstraction che mất domain owner.
- Không tạo package rỗng hoặc code dự phòng chưa có use case thực tế.
- Không viết query trả collection không giới hạn khi dữ liệu có thể tăng lớn.
- Không log password, access token, refresh token hoặc signed URL.
- Không merge thay đổi persistence nếu thiếu migration và test liên quan.

## 15. Quy tắc cần ghi nhớ

```text
Tổ chức theo domain.
Controller chỉ xử lý HTTP.
Application service điều phối flow.
Domain giữ model và business rule.
Infrastructure giữ chi tiết kỹ thuật.
Request, response và DTO không dùng lẫn nhau.
Không tạo abstraction khi chưa có nhu cầu thật.
Service interface luôn đi cùng implementation trong package impl.
Repository interface do Spring Data implement; gateway interface do infrastructure implement.
```

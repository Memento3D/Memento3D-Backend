# Quy tắc & Lưu ý dành cho Backend (BE)

> Tài liệu bắt buộc đọc trước khi viết API. PR không đáp ứng các mục **[BẮT BUỘC]** sẽ **không được merge**.

---

## 0. Definition of Done cho một API

Một API chỉ được coi là **xong** khi có đủ:

- [ ] Code chạy đúng nghiệp vụ, đúng convention của dự án
- [ ] Có test cho **từng layer** (mục 1)
- [ ] Coverage đạt **C0 = 100%** và **C1 ≥ 85%** (mục 1.2)
- [ ] Không có lỗi DB kinh điển: N+1, thiếu index, query không phân trang… (mục 2)
- [ ] Đã validate input, kiểm tra quyền, xử lý lỗi thống nhất (mục 3, 4)
- [ ] Có tài liệu API (OpenAPI/Swagger) và migration (nếu đổi schema)
- [ ] CI xanh (lint + test + coverage gate)

---

## 1. Quy tắc về TEST & COVERAGE **[BẮT BUỘC]**

### 1.1. Viết API nào phải có file test cho từng phần, từng layer

Mỗi API mới / sửa đổi **bắt buộc** đi kèm test ở **tất cả** các layer bị ảnh hưởng. Không được "để sau".

| Layer | Loại test | Cần mock gì | Kiểm tra cái gì |
|---|---|---|---|
| **Controller / Router / Handler** | Unit test (web layer) hoặc slice test | Mock Service | Route, HTTP method, status code, request/response mapping, validation input, auth/permission, format lỗi |
| **Service / Use case (business logic)** | Unit test | Mock Repository, client bên ngoài | Toàn bộ nhánh nghiệp vụ, rule, tính toán, exception, gọi đúng dependency (verify) |
| **Repository / DAO / Query** | Integration test với **DB thật** (Testcontainers/docker) | Không mock DB | Query đúng kết quả, index/constraint, phân trang, sort, **số lượng query (chống N+1)** |
| **Mapper / DTO converter / Validator / Util** | Unit test | Không cần | Mọi nhánh mapping, null, giá trị biên |
| **Middleware / Filter / Interceptor / Guard** | Unit test | Mock request/context | Pass / block / lỗi |
| **External client / Adapter (gọi API bên thứ 3)** | Unit test + contract test | Mock HTTP (WireMock/nock/…) | Timeout, 4xx, 5xx, retry, parse response lỗi |
| **End-to-end / API test** | Integration test toàn luồng | Chỉ mock hệ thống ngoài | Luồng chính từ request → DB → response |

**Cấu trúc file test** (mỗi file production có một file test tương ứng, đặt tên rõ ràng):

```
src/
  order/
    order.controller.ts
    order.service.ts
    order.repository.ts
    order.mapper.ts
tests/   (hoặc __tests__ / src/test/...)
  order/
    order.controller.spec.ts      # test layer controller
    order.service.spec.ts         # test layer service
    order.repository.int.spec.ts  # test layer repository (DB thật)
    order.mapper.spec.ts          # test mapper
    order.api.e2e.spec.ts         # test toàn luồng API
```

**Test case tối thiểu cho mỗi API** (áp dụng ở layer tương ứng):

1. **Happy path** – dữ liệu hợp lệ, trả kết quả đúng
2. **Validation fail** – thiếu field, sai kiểu, sai định dạng, quá dài/ngắn
3. **Giá trị biên** – 0, âm, rỗng, null, max length, ngày biên, page = 0/quá lớn
4. **401 / 403** – chưa đăng nhập, sai quyền, truy cập dữ liệu của người khác (IDOR)
5. **404** – không tồn tại
6. **409 / trùng lặp** – vi phạm unique, conflict version
7. **Lỗi hệ thống** – DB lỗi, service ngoài timeout/5xx → trả lỗi đúng format, không lộ stack trace
8. **Rollback** – lỗi giữa chừng transaction thì dữ liệu không bị ghi dở
9. **Idempotent** – gọi lặp lại (với API POST quan trọng như thanh toán, tạo đơn)

### 1.2. Ngưỡng coverage

| Chỉ số | Ý nghĩa | Ngưỡng bắt buộc |
|---|---|---|
| **C0** | Statement / Line coverage – mỗi câu lệnh phải được chạy ít nhất 1 lần | **100%** |
| **C1** | Branch / Decision coverage – mỗi nhánh `if/else`, `switch`, `?:`, `&&`/`||` được chạy cả true và false | **≥ 85%** |

Quy định đi kèm:

- Coverage được đo **trên code mới/sửa (diff coverage)** *và* trên toàn dự án. Cả hai đều phải đạt.
- **CI phải fail** nếu không đạt ngưỡng (coverage gate). Không hạ ngưỡng để "cho qua".
- **Không viết test giả để đẩy coverage**: test chỉ gọi hàm mà không `assert`/`verify` là **không hợp lệ** và sẽ bị reject khi review.
- Chỉ được loại trừ (exclude) khỏi coverage: file generated, class `main`/bootstrap, config thuần khai báo, DTO/Entity thuần getter-setter. Danh sách exclude phải nằm trong file cấu hình, có comment lý do và được **tech lead duyệt**. Cấm dùng `/* istanbul ignore */`, `@Generated`, `# pragma: no cover`… tuỳ tiện.
- Code khó test là **dấu hiệu thiết kế tệ** (hàm quá dài, phụ thuộc cứng, `new`/`static` khắp nơi) → refactor để test được, không né test.
- Khuyến nghị: chạy thêm **mutation testing** (PIT, Stryker, mutmut) định kỳ để chắc test thật sự bắt được lỗi.

### 1.3. Ví dụ cấu hình coverage gate

**Java – JaCoCo (Maven)**

```xml
<rule>
  <element>BUNDLE</element>
  <limits>
    <limit>
      <counter>LINE</counter>      <!-- C0 -->
      <value>COVEREDRATIO</value>
      <minimum>1.00</minimum>
    </limit>
    <limit>
      <counter>BRANCH</counter>    <!-- C1 -->
      <value>COVEREDRATIO</value>
      <minimum>0.85</minimum>
    </limit>
  </limits>
</rule>
```

**Node.js – Jest**

```js
// jest.config.js
module.exports = {
  collectCoverage: true,
  coverageThreshold: {
    global: { statements: 100, lines: 100, functions: 100, branches: 85 },
  },
};
```

**Python – pytest-cov**

```bash
# Lưu ý: khi bật --cov-branch, số % tổng gộp cả statement + branch.
# Nên kiểm tra riêng line và branch từ coverage.xml trong CI.
pytest --cov=app --cov-branch --cov-report=xml --cov-report=term-missing
```

**Go**

```bash
go test ./... -coverprofile=coverage.out
go tool cover -func=coverage.out
```
> Go chỉ đo statement coverage (C0). Muốn C1 cần công cụ bổ sung hoặc bảo đảm bằng review + table-driven test cho mọi nhánh.

### 1.4. Nguyên tắc viết test tốt

- Mỗi test **một mục đích**, tên nói rõ: `should_return_404_when_order_not_found`.
- Theo mẫu **Arrange – Act – Assert**.
- Test **độc lập**, chạy thứ tự nào cũng được, không phụ thuộc dữ liệu của test khác.
- **Không** gọi mạng thật, không phụ thuộc giờ hệ thống (inject `Clock`), không dùng `sleep`.
- Repository test dùng **DB cùng loại với production** (Testcontainers). Không dùng H2/SQLite thay Postgres/MySQL vì hành vi SQL khác nhau.
- Test data dùng builder/factory, không copy-paste JSON dài.
- Test chậm/flaky phải sửa hoặc xoá ngay, không để tồn đọng.

---

## 2. Quy tắc về DATABASE **[BẮT BUỘC]**

### 2.1. Lỗi N+1 query

**Là gì:** 1 query lấy danh sách N bản ghi, sau đó trong vòng lặp lại chạy thêm N query để lấy dữ liệu liên quan → tổng 1 + N query. Dữ liệu càng lớn hệ thống càng chậm, DB càng quá tải.

```java
// ❌ SAI – N+1 (JPA lazy loading)
List<Order> orders = orderRepo.findAll();               // 1 query
for (Order o : orders) {
    total += o.getItems().size();                       // N query
}

// ✅ ĐÚNG – lấy kèm bằng 1 query
@Query("select distinct o from Order o join fetch o.items where o.status = :status")
List<Order> findWithItems(@Param("status") Status status);
```

```js
// ❌ SAI – query trong vòng lặp
for (const o of orders) {
  o.items = await db.item.findMany({ where: { orderId: o.id } });
}

// ✅ ĐÚNG – gom ID, query 1 lần rồi group lại trong bộ nhớ
const items = await db.item.findMany({ where: { orderId: { in: orders.map(o => o.id) } } });
```

**Cách xử lý:**

- `JOIN FETCH` / `@EntityGraph` / `include` (Prisma, Sequelize) / `select_related`, `prefetch_related` (Django) / `Preload` (GORM).
- Batch load bằng `WHERE id IN (...)` hoặc `@BatchSize`; GraphQL dùng **DataLoader**.
- Với collection + phân trang: **không** `join fetch` collection kèm `LIMIT` (bị phân trang trong bộ nhớ). Làm 2 bước: lấy page ID trước, rồi lấy dữ liệu theo danh sách ID.
- Chỉ cần vài cột → dùng projection/DTO thay vì load cả entity.

**Cách phát hiện & chặn:**

- **Bắt buộc** có test đếm số query cho các API trả danh sách/quan hệ: số query phải **cố định**, không tăng theo số bản ghi (dùng `datasource-proxy`/Hibernate Statistics, query logger, `assertNumQueries` của Django…).
- Test với dữ liệu ≥ 2 và ≥ 10 bản ghi, khẳng định số query bằng nhau.
- Bật log SQL ở môi trường dev/test; review kỹ mọi vòng lặp có chứa lời gọi repository/DB.
- **Cấm** gọi repository/DB bên trong `for`, `forEach`, `map`, `stream`.

### 2.2. Các lỗi DB thường gặp khác

| # | Lỗi | Quy tắc |
|---|---|---|
| 1 | **Thiếu index** | Mọi cột dùng trong `WHERE`, `JOIN`, `ORDER BY` của query thường xuyên phải có index phù hợp (composite index đúng thứ tự cột). Kiểm tra bằng `EXPLAIN`/`EXPLAIN ANALYZE`, tránh full table scan trên bảng lớn. |
| 2 | **`SELECT *`** | Chỉ select cột cần thiết. Tránh load các cột lớn (TEXT/BLOB/JSON) khi không dùng. |
| 3 | **List không phân trang** | API danh sách **bắt buộc** phân trang, có `limit` tối đa (vd. 100). Cấm `findAll()` trên bảng lớn. |
| 4 | **`OFFSET` lớn** | Trang sâu rất chậm → dùng **keyset/cursor pagination** (`WHERE id > :last ORDER BY id LIMIT n`). |
| 5 | **Hàm/ép kiểu trên cột có index** | `WHERE DATE(created_at) = ...`, `LOWER(email) = ...` làm mất index → viết lại dạng range, hoặc tạo functional index. |
| 6 | **`LIKE '%keyword%'`** | Không dùng được index thường → dùng full-text search / search engine. |
| 7 | **Insert/Update từng dòng trong vòng lặp** | Dùng **batch insert/update** (`saveAll` + `jdbc batch`, `createMany`, `bulk_create`), chia chunk hợp lý. |
| 8 | **`COUNT(*)` trên bảng lớn** | Tránh đếm tổng không cần thiết; dùng ước lượng, cache, hoặc chỉ trả `hasNext`. |
| 9 | **Transaction quá dài / sai phạm vi** | Transaction phải **ngắn nhất có thể**. **Không** gọi HTTP/API ngoài, gửi mail, sleep, xử lý nặng bên trong transaction. |
| 10 | **Thiếu transaction** | Nhiều thao tác ghi phải cùng thành công hoặc cùng rollback → đặt trong 1 transaction. Chú ý `@Transactional` không chạy khi gọi nội bộ cùng class (self-invocation). |
| 11 | **Lost update / race condition** | Dùng **optimistic locking** (cột `version`) hoặc `SELECT ... FOR UPDATE` khi cần. Cập nhật số dư/tồn kho dùng câu lệnh atomic (`SET stock = stock - 1 WHERE stock > 0`), không đọc-rồi-ghi. |
| 12 | **Deadlock** | Luôn lock tài nguyên theo **cùng một thứ tự**; giữ lock ngắn; có cơ chế retry khi deadlock. |
| 13 | **Ràng buộc chỉ ở tầng code** | Unique, FK, NOT NULL, CHECK **phải có ở DB**, không chỉ kiểm tra trong code (tránh race condition). |
| 14 | **Lazy loading ngoài transaction** | Gây `LazyInitializationException` hoặc query ngầm. Map sang DTO **trong** transaction; không trả Entity ra ngoài. |
| 15 | **Connection leak / pool cạn** | Luôn đóng connection/cursor/stream; cấu hình pool size và timeout hợp lý; không giữ connection khi chờ I/O khác. |
| 16 | **Migration nguy hiểm** | Migration phải versioned (Flyway/Liquibase/Alembic/Prisma migrate), **backward compatible** (thêm cột nullable/default trước, xoá sau). Bảng lớn: tạo index `CONCURRENTLY`/online, tránh `ALTER` khoá bảng. Có kế hoạch rollback. |
| 17 | **Dùng `float/double` cho tiền** | Dùng `DECIMAL/NUMERIC` (hoặc lưu đơn vị nhỏ nhất dạng integer). |
| 18 | **Múi giờ** | Lưu thời gian dạng **UTC** (`timestamptz`), chuyển đổi múi giờ ở tầng hiển thị. |
| 19 | **Soft delete quên lọc / quên index** | Mọi query phải lọc `deleted_at IS NULL` (dùng global filter), unique index cần điều kiện phù hợp. |
| 20 | **Cache sai** | Có chiến lược invalidate rõ ràng, TTL hợp lý; tránh cache stampede; không cache dữ liệu nhạy cảm theo user dùng chung key. |
| 21 | **Query động nối chuỗi** | Luôn dùng **parameterized query / prepared statement** – chống SQL Injection (mục 4). |
| 22 | **Kết quả quá lớn vào bộ nhớ** | Dữ liệu lớn dùng streaming/cursor/chunk, không `toList()` toàn bộ. |
| 23 | **Slow query không được giám sát** | Bật slow query log, đặt ngưỡng cảnh báo; review query plan trước khi đưa các query mới lên production. |

---

## 3. Quy tắc thiết kế API

- **REST convention:** danh từ số nhiều cho resource, đúng HTTP method (`GET` không gây side-effect, `PUT/PATCH/DELETE` idempotent).
- **Status code đúng nghĩa:** `200/201/204`, `400` (input sai), `401`, `403`, `404`, `409`, `422`, `429`, `500`. Không trả `200` kèm lỗi trong body.
- **Format lỗi thống nhất** toàn hệ thống, ví dụ:
  ```json
  { "code": "ORDER_NOT_FOUND", "message": "Order not found", "traceId": "abc-123" }
  ```
- **Validate mọi input** ở biên (body, query, path, header): kiểu, độ dài, range, enum, format. Không tin dữ liệu từ client.
- **DTO tách biệt Entity:** không trả/nhận trực tiếp Entity; chống lộ field nội bộ và mass assignment.
- **Phân trang, sort, filter** chuẩn hoá; giới hạn `limit` tối đa; whitelist field được sort/filter.
- **Idempotency:** API tạo/thanh toán quan trọng hỗ trợ `Idempotency-Key` để client retry an toàn.
- **Versioning & backward compatibility:** không phá vỡ contract cũ; thay đổi breaking phải tăng version (`/v2`) và có thời gian deprecate.
- **Tài liệu:** cập nhật OpenAPI/Swagger cùng lúc với code.
- **Không** chứa business logic trong Controller; Controller chỉ nhận request → gọi Service → trả response.

---

## 4. Bảo mật

- **SQL Injection:** chỉ dùng parameterized query/ORM binding. Cấm nối chuỗi từ input vào SQL.
- **Xác thực & phân quyền:** mọi endpoint mặc định phải yêu cầu auth; endpoint public phải khai báo rõ. Luôn kiểm tra **quyền sở hữu dữ liệu** (chống IDOR/BOLA: user A không được đọc/sửa dữ liệu user B chỉ bằng đổi `id`).
- **Mass assignment:** chỉ map các field cho phép từ request, không bind thẳng toàn bộ body vào entity.
- **Secrets:** không commit password/token/key vào repo; dùng biến môi trường hoặc secret manager. Đã lỡ commit → coi như đã lộ, phải rotate ngay.
- **Mật khẩu:** hash bằng `bcrypt`/`argon2`, tuyệt đối không lưu plain text hay MD5/SHA1.
- **Không log dữ liệu nhạy cảm:** password, token, số thẻ, CCCD, PII. Che (mask) khi cần log.
- **Rate limiting** cho login, OTP, các API tốn tài nguyên.
- **Upload file:** kiểm tra loại, kích thước, đổi tên file, không thực thi file upload, quét virus nếu cần.
- **CORS, HTTPS, security headers** cấu hình chặt, không để `*` tuỳ tiện.
- **Dependency:** quét lỗ hổng thư viện (Dependabot, `npm audit`, OWASP Dependency-Check) trong CI.

---

## 5. Xử lý lỗi & Logging

- Dùng **global exception handler**; không để lộ stack trace, câu SQL, đường dẫn nội bộ ra response.
- **Không nuốt exception** (`catch {}` rỗng). Bắt thì phải xử lý hoặc log rồi ném lại.
- Phân biệt **lỗi nghiệp vụ** (4xx, không cần alert) và **lỗi hệ thống** (5xx, cần alert).
- Log có **traceId/correlationId** xuyên suốt request; log dạng structured (JSON).
- Dùng đúng level: `ERROR` (cần người xử lý), `WARN`, `INFO` (sự kiện nghiệp vụ chính), `DEBUG` (chỉ dev).
- Không log trong vòng lặp lớn; không log toàn bộ payload lớn.
- Có **health check** (`/health`, readiness/liveness) và **metrics** (latency, error rate, số query, pool usage).

---

## 6. Gọi hệ thống ngoài & xử lý bất đồng bộ

- Mọi lời gọi ra ngoài **phải có timeout** (connect + read). Không để mặc định vô hạn.
- **Retry** chỉ với thao tác idempotent, có **exponential backoff + jitter** và giới hạn số lần.
- Dùng **circuit breaker / bulkhead** cho dịch vụ hay lỗi để tránh lỗi dây chuyền.
- Việc chậm/không cần trả ngay (gửi mail, xuất báo cáo, đồng bộ) → đẩy qua **queue/job nền**; API trả nhanh.
- Consumer queue phải **idempotent** (nhận trùng message vẫn đúng), có **dead-letter queue** và cơ chế retry.
- Dùng **Outbox pattern** khi cần vừa ghi DB vừa phát event đảm bảo nhất quán.
- Thao tác song song: chú ý thread-safety, tránh biến `static`/singleton giữ state theo request.

---

## 7. Chất lượng code & quy trình

- Tuân thủ layer: `Controller → Service → Repository`. Không gọi ngược, không bỏ qua layer.
- Hàm ngắn, một trách nhiệm; tránh class "thần thánh" (God class); tránh trùng lặp code.
- Không hard-code cấu hình (URL, key, timeout, limit) – đưa vào config/env theo từng môi trường.
- Tên biến/hàm rõ nghĩa; comment giải thích **vì sao**, không giải thích **cái gì**.
- Xoá code chết, TODO không có người/ticket phụ trách.
- **Code review:** ít nhất 1 reviewer; reviewer phải kiểm tra checklist bên dưới.
- CI bắt buộc: build, lint/format, test, coverage gate, quét bảo mật. PR đỏ không được merge.
- Commit/PR nhỏ, có mô tả rõ mục đích, link ticket, cách test.

---

## 8. CHECKLIST cho PR (tác giả tự tick, reviewer kiểm tra lại)

**Test & Coverage**
- [ ] Có file test cho Controller, Service, Repository, Mapper/Util và các layer liên quan
- [ ] Đủ các case: happy path, validation, biên, 401/403, 404, conflict, lỗi hệ thống, rollback
- [ ] **C0 = 100%**, **C1 ≥ 85%** (cả toàn dự án và diff mới)
- [ ] Test có assertion thật, không phải test giả để tăng coverage
- [ ] Repository test chạy trên DB thật (Testcontainers), không dùng DB khác loại

**Database**
- [ ] Không có N+1 (đã có test đếm số query cho API danh sách/quan hệ)
- [ ] Không có query/DB call trong vòng lặp
- [ ] Đã có index cho `WHERE/JOIN/ORDER BY`, đã kiểm tra `EXPLAIN`
- [ ] API danh sách có phân trang + giới hạn `limit`
- [ ] Không `SELECT *`, không load dư dữ liệu
- [ ] Transaction ngắn, đúng phạm vi, không gọi API ngoài bên trong
- [ ] Xử lý race condition (optimistic lock / atomic update) nếu có ghi đồng thời
- [ ] Ràng buộc unique/FK/NOT NULL có ở DB
- [ ] Migration an toàn, backward compatible, có rollback

**API & Bảo mật**
- [ ] Validate input đầy đủ; dùng DTO, không lộ Entity
- [ ] Có kiểm tra auth + quyền sở hữu dữ liệu (chống IDOR)
- [ ] Parameterized query, không nối chuỗi SQL
- [ ] Không log dữ liệu nhạy cảm; không commit secret
- [ ] Format lỗi và status code đúng chuẩn
- [ ] Đã cập nhật OpenAPI/Swagger

**Vận hành**
- [ ] Lời gọi hệ thống ngoài có timeout/retry/circuit breaker hợp lý
- [ ] Có log + traceId + metrics cho luồng mới
- [ ] Cấu hình lấy từ env, không hard-code

---

> **Ghi nhớ:** Coverage cao là điều kiện **cần**, không phải điều kiện **đủ**. Mục tiêu thật sự là test phải bắt được lỗi, và code phải chạy tốt khi dữ liệu lớn.

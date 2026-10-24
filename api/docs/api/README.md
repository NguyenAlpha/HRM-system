# Tài liệu API HRM

Các endpoint trả envelope `ApiResult`. Trừ đăng nhập, refresh và kích hoạt account, endpoint đều yêu cầu JWT hợp lệ; quyền cụ thể được kiểm tra tại service bằng permission và phạm vi dữ liệu.

## Danh mục hiện hành

Cột thứ hai liệt kê đúng path prefix do tài liệu đó mô tả, dùng để tra ngược từ một endpoint về
tài liệu tương ứng. Các nhóm endpoint đang chạy đều nằm trong bảng. Muốn xem bức tranh tính
năng theo module thay vì theo endpoint, đọc [docs/FEATURES.md](../../../docs/FEATURES.md).

| Tài liệu | Path prefix |
| --- | --- |
| [AUTH.md](AUTH.md) | `/api/auth/*`, `/api/account-activations/*` |
| [ACCOUNT.md](ACCOUNT.md) | `/api/accounts`, `/api/accounts/{accountId}` cùng các hành động `suspend`, `activate`, `invitations/resend`, `password-reset` |
| [ACCOUNT_ROLE_ASSIGNMENTS.md](ACCOUNT_ROLE_ASSIGNMENTS.md) | `/api/accounts/{accountId}/role-assignments` |
| [ACCOUNT_PERMISSION_OVERRIDES.md](ACCOUNT_PERMISSION_OVERRIDES.md) | `/api/accounts/{accountId}/role-assignments/{assignmentId}/permission-overrides` |
| [RBAC.md](RBAC.md) | `/api/roles/*`, `/api/permissions/*` |
| [ROLE_ASSIGNMENT_REQUESTS.md](ROLE_ASSIGNMENT_REQUESTS.md) | `/api/role-assignment-requests/*` |
| [EMPLOYEE.md](EMPLOYEE.md) | `/api/employees/*`, `/api/employees/{employeeId}/assignments`, `/api/employees/{employeeId}/sensitive`, `/api/resignation-requests/*` |
| [ORGANIZATION.md](ORGANIZATION.md) | `/api/organization-units/*`, `/api/work-locations/*`, `/api/job-positions/*`, `/api/company` |
| [ORGANIZATION_COMPANY_OWNER_ADMIN.md](ORGANIZATION_COMPANY_OWNER_ADMIN.md) | `/api/system/organization/company-owner` |
| [RATE_LIMIT.md](../concepts/RATE_LIMIT.md#7-thống-kê) | `/api/system/rate-limit/stats`, `/api/system/rate-limit/timeline`, `/api/system/rate-limit/policies/{policy}` |
| [API_TRAFFIC.md](../concepts/API_TRAFFIC.md#2-api) | `/api/system/traffic/stats` |
| [WORK_SHIFT.md](WORK_SHIFT.md) | `/api/work-shifts/*` |
| [ATTENDANCE.md](ATTENDANCE.md) | `/api/attendance/*`, `/api/leave-requests/*`, `/api/company-holidays/*`, `/api/leave-entitlements/*` |
| [NOTIFICATIONS.md](NOTIFICATIONS.md) | `/api/notifications/*` |
| [PAYROLL.md](PAYROLL.md) | `/api/payroll/*`, `/api/compensation/employees/{employeeId}/*` |
| [ALLOWANCE.md](ALLOWANCE.md) | `/api/compensation/position-allowances`, `/api/compensation/job-positions/{jobPositionId}/allowances`, `/api/compensation/seniority-allowances` |
| [REPORT.md](REPORT.md) | `/api/reports/hr/*`, `/api/reports/payroll/*` |

Hai chỗ dễ tra nhầm: `role-assignments` và `permission-overrides` nằm dưới `/api/accounts` nhưng
có tài liệu riêng, còn `/api/compensation/employees/*` thuộc `PAYROLL.md` chứ không phải `EMPLOYEE.md`; chính sách
phụ cấp chung của `/api/compensation/*` nằm ở `ALLOWANCE.md`.

Cơ chế xuyên suốt nhiều endpoint (phạm vi dữ liệu, token, vòng đời kỳ lương, dữ liệu theo khoảng hiệu lực) được giải
thích riêng trong [tài liệu cơ chế](../concepts/README.md).

## Quy ước chung

- Actor/audit lấy từ JWT hiện tại; client không gửi account ID người thao tác.
- Mọi endpoint đều có giới hạn số request; hết lượt thì trả `429 RATE_LIMIT_EXCEEDED` kèm `Retry-After`
  ([RATE_LIMIT.md](../concepts/RATE_LIMIT.md)).
- Permission catalog do code sở hữu và chỉ đọc. API role hỗ trợ CRUD custom role và gán permission `DELEGABLE`.
- Scope gồm `SELF`, `ORG_UNIT`, `LOCATION`, `COMPANY`; quyền UI không thay thế authorization backend.
- Ngày nghiệp vụ dùng ISO `YYYY-MM-DD`; thời điểm dùng ISO-8601 và lưu UTC.
- Lỗi nghiệp vụ trả `success:false` cùng `error.code`, `message` và `field` nếu có. Lỗi validation của
  request DTO có `message` tiếng Việt (`src/main/resources/ValidationMessages.properties`), ví dụ
  `field: "name"`, `message: "phải có từ 0 đến 40 ký tự"`; các message khác là tiếng Anh dành cho
  developer, web tự hiển thị câu tiếng Việt theo `error.code`.
- Đường dẫn không có endpoint trả `404 RESOURCE_NOT_FOUND`; method không được endpoint hỗ trợ trả
  `405 METHOD_NOT_ALLOWED` kèm header `Allow`. Chỉ lỗi ngoài dự kiến mới trả `500 INTERNAL_ERROR`.

## OpenAPI và Swagger UI

Khi API chạy local, springdoc sinh spec từ controller và DTO:

| URL | Nội dung |
| --- | --- |
| `http://localhost:8080/swagger-ui.html` | Swagger UI để xem và gọi thử endpoint |
| `http://localhost:8080/v3/api-docs` | Spec OpenAPI 3.1 dạng JSON |

Hai URL này không cần đăng nhập. Để gọi endpoint cần JWT trong Swagger UI, lấy `accessToken` từ
`POST /api/auth/login` rồi dán vào nút **Authorize**. Đặt `API_DOCS_ENABLED=false` để tắt cả hai
ngoài môi trường phát triển.

Spec chỉ phản ánh đúng kiểu dữ liệu và ràng buộc validation; quy tắc nghiệp vụ, permission và mã
lỗi vẫn được mô tả trong các tài liệu ở bảng trên.

Web sinh TypeScript type từ bản chụp spec `apps/web/openapi.json`. Đổi DTO hoặc endpoint mà
không cập nhật bản chụp thì `OpenApiSnapshotTest` fail; cách cập nhật xem
[DEVELOPMENT.md](../../../apps/web/docs/DEVELOPMENT.md#kiểu-dữ-liệu-sinh-từ-api). Vì type được
sinh từ spec, DTO phải khai báo đúng khả năng null:

- **Response:** API luôn trả đủ mọi field, kể cả field `null`, nên `OpenApiConfig` đánh dấu mọi
  field là `required`. Field có thể `null` phải gắn `jakarta.annotation.Nullable`; field không gắn
  được coi là luôn có giá trị.
- **Request:** field có `@NotNull`/`@NotBlank` là bắt buộc. Field không bắt buộc và nhận `null`
  phải gắn `@Nullable` để web được phép gửi `null`.

Mọi DTO mà web đang dùng đã được gắn đầy đủ, kể cả các record lồng trong service (ví dụ
`LeaveRequestService.LeaveRequestView`) và envelope `ApiResult`/`ErrorDetail`. Các command record
lồng trong service (`CreateLeaveCommand`, `SetSalaryCommand`, `ProfileCommand`, `DependentCommand`,
`EndDependentCommand`, `SetPositionAllowanceCommand`, `SetSeniorityAllowanceCommand`) cũng dùng Bean Validation như request DTO thường: body sai trả
`400 VALIDATION_ERROR` kèm `field` trước khi service kiểm tra quyền. Service chỉ giữ các quy tắc cần
dữ liệu khác, như `endAt` sau `startAt` hay `effectiveFrom` không trước ngày vào làm.

## Nguồn sự thật

Khi tài liệu và code khác nhau, ưu tiên theo thứ tự: migration và controller/service đang chạy, test, tài liệu trong thư mục này, rồi các bản thiết kế lịch sử trong `api/docs/archive/`.

Schema hiện hành được tạo bởi các migration Flyway trong `api/src/main/resources/db/migration/` và được mô tả tại [database/DATABASE_SCHEMA.md](../database/DATABASE_SCHEMA.md). Các tài liệu trong `api/docs/archive/` là thiết kế cũ trước schema hiện hành, chỉ dùng để tham khảo lịch sử.

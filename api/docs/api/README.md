# Tài liệu API HRM

Các endpoint trả envelope `ApiResult`. Trừ đăng nhập, refresh và kích hoạt account, endpoint đều yêu cầu JWT hợp lệ; quyền cụ thể được kiểm tra tại service bằng permission và phạm vi dữ liệu.

## Danh mục hiện hành

Cột thứ hai liệt kê đúng path prefix do tài liệu đó mô tả, dùng để tra ngược từ một endpoint về
tài liệu tương ứng. Toàn bộ 22 controller đang chạy đều nằm trong bảng. Muốn xem bức tranh tính
năng theo module thay vì theo endpoint, đọc [docs/FEATURES.md](../../../docs/FEATURES.md).

| Tài liệu | Path prefix |
| --- | --- |
| [AUTH.md](AUTH.md) | `/api/auth/*`, `/api/account-activations/*` |
| [ACCOUNT.md](ACCOUNT.md) | `/api/accounts`, `/api/accounts/{accountId}` cùng các hành động `suspend`, `activate`, `invitations/resend`, `password-reset` |
| [ACCOUNT_ROLE_ASSIGNMENTS.md](ACCOUNT_ROLE_ASSIGNMENTS.md) | `/api/accounts/{accountId}/role-assignments` |
| [ACCOUNT_PERMISSION_OVERRIDES.md](ACCOUNT_PERMISSION_OVERRIDES.md) | `/api/accounts/{accountId}/role-assignments/{assignmentId}/permission-overrides` |
| [RBAC.md](RBAC.md) | `/api/roles/*`, `/api/permissions/*` |
| [ROLE_ASSIGNMENT_REQUESTS.md](ROLE_ASSIGNMENT_REQUESTS.md) | `/api/role-assignment-requests/*` |
| [EMPLOYEE.md](EMPLOYEE.md) | `/api/employees/*`, `/api/employees/{employeeId}/assignments`, `/api/employees/{employeeId}/sensitive` |
| [ORGANIZATION.md](ORGANIZATION.md) | `/api/organization-units/*`, `/api/work-locations/*`, `/api/job-positions/*` |
| [ORGANIZATION_COMPANY_OWNER_ADMIN.md](ORGANIZATION_COMPANY_OWNER_ADMIN.md) | `/api/system/organization/company-owner` |
| [WORK_SHIFT.md](WORK_SHIFT.md) | `/api/work-shifts/*` |
| [ATTENDANCE.md](ATTENDANCE.md) | `/api/attendance/*`, `/api/leave-requests/*`, `/api/company-holidays/*`, `/api/leave-entitlements/*` |
| [PAYROLL.md](PAYROLL.md) | `/api/payroll/*`, `/api/compensation/employees/{employeeId}/*` |
| [REPORT.md](REPORT.md) | `/api/reports/hr/*`, `/api/reports/payroll/*` |

Hai chỗ dễ tra nhầm: `role-assignments` và `permission-overrides` nằm dưới `/api/accounts` nhưng
có tài liệu riêng, còn `/api/compensation/*` thuộc `PAYROLL.md` chứ không phải `EMPLOYEE.md`.

## Quy ước chung

- Actor/audit lấy từ JWT hiện tại; client không gửi account ID người thao tác.
- Permission catalog do code sở hữu và chỉ đọc. API role hỗ trợ CRUD custom role và gán permission `DELEGABLE`.
- Scope gồm `SELF`, `ORG_UNIT`, `LOCATION`, `COMPANY`; quyền UI không thay thế authorization backend.
- Ngày nghiệp vụ dùng ISO `YYYY-MM-DD`; thời điểm dùng ISO-8601 và lưu UTC.
- Lỗi nghiệp vụ trả `success:false` cùng `error.code`, `message` và `field` nếu có.
- Đường dẫn không có endpoint trả `404 RESOURCE_NOT_FOUND`; method không được endpoint hỗ trợ trả
  `405 METHOD_NOT_ALLOWED` kèm header `Allow`. Chỉ lỗi ngoài dự kiến mới trả `500 INTERNAL_ERROR`.

## Nguồn sự thật

Khi tài liệu và code khác nhau, ưu tiên theo thứ tự: migration và controller/service đang chạy, test, tài liệu trong thư mục này, rồi các bản thiết kế lịch sử trong `api/docs/archive/`.

Schema hiện hành được tạo bởi các migration Flyway trong `api/src/main/resources/db/migration/` và được mô tả tại [database/DATABASE_SCHEMA.md](../database/DATABASE_SCHEMA.md). Các tài liệu trong `api/docs/archive/` là thiết kế cũ trước schema hiện hành, chỉ dùng để tham khảo lịch sử.

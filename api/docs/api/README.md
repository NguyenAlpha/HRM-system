# Tài liệu API HRM

Các endpoint trả envelope `ApiResult`. Trừ đăng nhập, refresh và kích hoạt account, endpoint đều yêu cầu JWT hợp lệ; quyền cụ thể được kiểm tra tại service bằng permission và phạm vi dữ liệu.

## Danh mục hiện hành

| Tài liệu | API chính |
| --- | --- |
| [AUTH.md](AUTH.md) | `/api/auth/*`, `/api/account-activations/*` |
| [ACCOUNT.md](ACCOUNT.md) | `/api/accounts`, vòng đời account nhân viên |
| [ACCOUNT_ROLE_ASSIGNMENTS.md](ACCOUNT_ROLE_ASSIGNMENTS.md) | `/api/accounts/{id}/role-assignments` |
| [ACCOUNT_PERMISSION_OVERRIDES.md](ACCOUNT_PERMISSION_OVERRIDES.md) | `/api/accounts/{id}/role-assignments/{assignmentId}/permission-overrides` |
| [RBAC.md](RBAC.md) | `/api/roles`, `/api/permissions` |
| [ROLE_ASSIGNMENT_REQUESTS.md](ROLE_ASSIGNMENT_REQUESTS.md) | `/api/role-assignment-requests` |
| [EMPLOYEE.md](EMPLOYEE.md) | `/api/employees`, phân công và dữ liệu nhạy cảm |
| [ORGANIZATION.md](ORGANIZATION.md) | `/api/organization-units`, `/api/work-locations`, `/api/job-positions` |
| [ORGANIZATION_COMPANY_OWNER_ADMIN.md](ORGANIZATION_COMPANY_OWNER_ADMIN.md) | `/api/system/organization/company-owner` |
| [WORK_SHIFT.md](WORK_SHIFT.md) | `/api/work-shifts` |
| [ATTENDANCE.md](ATTENDANCE.md) | ngày lễ, nghỉ phép, chấm công và tăng ca |
| [PAYROLL.md](PAYROLL.md) | lương, hồ sơ bảo hiểm, người phụ thuộc, thuế và phiếu lương |
| [REPORT.md](REPORT.md) | `/api/reports/hr/*`, `/api/reports/payroll/*` |

## Quy ước chung

- Actor/audit lấy từ JWT hiện tại; client không gửi account ID người thao tác.
- Permission catalog do code sở hữu và chỉ đọc. API role hỗ trợ CRUD custom role và gán permission `DELEGABLE`.
- Scope gồm `SELF`, `ORG_UNIT`, `LOCATION`, `COMPANY`; quyền UI không thay thế authorization backend.
- Ngày nghiệp vụ dùng ISO `YYYY-MM-DD`; thời điểm dùng ISO-8601 và lưu UTC.
- Lỗi nghiệp vụ trả `success:false` cùng `error.code`, `message` và `field` nếu có.

## Nguồn sự thật

Khi tài liệu và code khác nhau, ưu tiên theo thứ tự: migration và controller/service đang chạy, test, tài liệu trong thư mục này, rồi các bản thiết kế lịch sử trong `api/docs/database/`.

Schema hiện hành được tạo bởi Flyway V1–V34. [Fix_database/DATABASE_SCHEMA.md](../Fix_database/DATABASE_SCHEMA.md) mô tả thiết kế lõi và phụ lục migration mới; các tài liệu trong `api/docs/database/` chỉ dùng để tham khảo lịch sử.

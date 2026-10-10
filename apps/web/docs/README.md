# HRM Web Documentation

Tài liệu dành cho ứng dụng Next.js trong `apps/web`.

## Danh mục

| Tài liệu | Nội dung |
|:---------|:---------|
| [DEVELOPMENT.md](./DEVELOPMENT.md) | Chuẩn bị môi trường, chạy local, cấu hình và checklist kiểm thử |
| [AUTH.md](./AUTH.md) | Kiến trúc xác thực BFF, hai cổng đăng nhập, session API và cookie |
| [RBAC.md](./RBAC.md) | Trang quản lý custom role và đồng bộ permission theo module |

## Phạm vi hiện tại

Web hiện cung cấp hai khu vực độc lập:

| Khu vực | Trang đăng nhập | Trang sau đăng nhập | Đối tượng |
|:--------|:----------------|:--------------------|:----------|
| HRM Workspace | `/login` | `/dashboard` | Tài khoản không có role `SYSTEM_ADMIN` |
| Admin Console | `/admin/login` | `/admin` | Tài khoản có role `SYSTEM_ADMIN` |

### HRM Workspace

| Route | Nội dung |
|:------|:---------|
| `/dashboard` | Tổng quan phiên đăng nhập và lối tắt nghiệp vụ |
| `/employees`, `/employees/{employeeId}` | Hồ sơ, phân công, lương và vòng đời nhân viên |
| `/organization` | Đơn vị tổ chức, địa điểm, vị trí và ca làm |
| `/attendance` | Chấm công, chuẩn bị tháng, xử lý dữ liệu thiếu và tăng ca |
| `/leave-requests` | Đơn nghỉ, quy trình phê duyệt, số dư phép năm và điều chỉnh hạn mức của HR |
| `/payslips` | Hồ sơ bảo hiểm, người phụ thuộc, kỳ lương và phiếu lương |
| `/reports/workforce` | Báo cáo nhân sự, thâm niên và lương cơ bản |
| `/rbac`, `/role-grant`, `/role-requests` | Quản lý quyền nghiệp vụ theo permission được cấp |

### Admin Console

| Route | Nội dung |
|:------|:---------|
| `/admin` | Tổng quan quản trị hệ thống |
| `/admin/company-owner` | Khởi tạo Company Owner đầu tiên |
| `/admin/organization` | Dữ liệu tổ chức phục vụ bootstrap |
| `/admin/rbac` | CRUD custom role, xem permission và đồng bộ quyền |
| `/admin/role-grant` | Gán hoặc thu hồi role theo chính sách backend |

Browser gọi API qua BFF và không nhận JWT. Việc hiển thị nút dựa trên permission chỉ phục vụ giao diện; backend tiếp tục kiểm tra permission và scope cho mọi thao tác.

Tài liệu Spring Boot API nằm tại [mục lục API](../../../api/docs/api/README.md). Quy trình chấm công và lương được mô tả riêng trong [ATTENDANCE.md](../../../api/docs/api/ATTENDANCE.md) và [PAYROLL.md](../../../api/docs/api/PAYROLL.md).

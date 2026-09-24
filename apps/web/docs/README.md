# HRM Web Documentation

Tài liệu dành cho ứng dụng Next.js trong `apps/web`.

## Danh mục

| Tài liệu | Nội dung |
|:---------|:---------|
| [DEVELOPMENT.md](./DEVELOPMENT.md) | Chuẩn bị môi trường, chạy local, cấu hình và checklist kiểm thử |
| [AUTH.md](./AUTH.md) | Kiến trúc xác thực BFF, hai cổng đăng nhập, session API và cookie |
| [RBAC.md](./RBAC.md) | Trang admin quản lý role, permission và gán/gỡ quyền |

## Phạm vi hiện tại

Web hiện cung cấp hai khu vực độc lập:

| Khu vực | Trang đăng nhập | Trang sau đăng nhập | Đối tượng |
|:--------|:----------------|:--------------------|:----------|
| HRM Workspace | `/login` | `/dashboard` | Tài khoản không có role `SYSTEM_ADMIN` |
| Admin Console | `/admin/login` | `/admin` | Tài khoản có role `SYSTEM_ADMIN` |

Các màn hình hiện tại phục vụ kiểm thử luồng xác thực, refresh token, đổi mật khẩu và
đăng xuất. Trang `/admin/rbac` hỗ trợ CRUD role/permission và gán/gỡ quyền cho role.
Các module nghiệp vụ như hồ sơ, chấm công, đơn từ và phiếu lương chưa được triển khai trên web.

Tài liệu API Spring Boot nằm tại
[`api/docs/api/AUTH.md`](../../../api/docs/api/AUTH.md).

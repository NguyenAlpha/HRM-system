# Tài liệu cơ chế (Concepts)

Tài liệu trong thư mục này giải thích **cơ chế chạy thế nào và vì sao**, khác với [tài liệu API](../api/README.md)
(mỗi endpoint nhận gì, trả gì). Đọc ở đây khi quên một quy tắc, trước khi phải đọc code.

| Tài liệu | Nội dung |
|:---------|:---------|
| [DATA_SCOPE.md](./DATA_SCOPE.md) | Scoped RBAC: role gán kèm phạm vi `SELF` / `ORG_UNIT` / `LOCATION` / `COMPANY`, cách tính và áp vào truy vấn |
| [TOKEN_LIFECYCLE.md](./TOKEN_LIFECYCLE.md) | Access/refresh/activation token, xoay vòng, phát hiện dùng lại, khi nào mọi phiên bị thu hồi |
| [PAYROLL_LIFECYCLE.md](./PAYROLL_LIFECYCLE.md) | Trạng thái kỳ lương, điều kiện chuyển trạng thái, xử lý thay đổi hồi tố |
| [EFFECTIVE_DATING.md](./EFFECTIVE_DATING.md) | Dữ liệu theo khoảng hiệu lực, ràng buộc `EXCLUDE USING GIST`, múi giờ của "hôm nay" |
| [APPROVAL_ROUTING.md](./APPROVAL_ROUTING.md) | Ai duyệt đơn nghỉ, đơn nghỉ việc và tăng ca: chuỗi quản lý trực tiếp → Giám đốc → Company Owner |
| [ROLE_GRANT_POLICY.md](./ROLE_GRANT_POLICY.md) | `grant_policy` của role, các đường cấp/thu hồi role, guard bảo vệ tài khoản cấp cao |
| [RATE_LIMIT.md](./RATE_LIMIT.md) | Giới hạn request theo IP, người dùng và số lần sai mật khẩu; cấu hình, proxy tin cậy, thống kê |
| [API_TRAFFIC.md](./API_TRAFFIC.md) | Thống kê số request, thời gian xử lý và lỗi theo từng API từ Micrometer |
| [ERROR_HANDLING.md](./ERROR_HANDLING.md) | Envelope lỗi, exception → HTTP status, cách web hiện lỗi tiếng Việt, thêm mã lỗi mới |

Lý do của các quyết định thiết kế lớn (BFF, kiểm tra quyền ở service, lọc phạm vi ở ứng dụng, ràng buộc exclusion)
được ghi trong [Architecture Decision Records](../../../docs/adr/README.md).

# Tài liệu cơ chế (Concepts)

Tài liệu trong thư mục này giải thích **cơ chế chạy thế nào và vì sao**, khác với [tài liệu API](../api/README.md)
(mỗi endpoint nhận gì, trả gì). Đọc ở đây khi quên một quy tắc, trước khi phải đọc code.

| Tài liệu | Nội dung |
|:---------|:---------|
| [DATA_SCOPE.md](./DATA_SCOPE.md) | Scoped RBAC: role gán kèm phạm vi `SELF` / `ORG_UNIT` / `LOCATION` / `COMPANY`, cách tính và áp vào truy vấn |
| [TOKEN_LIFECYCLE.md](./TOKEN_LIFECYCLE.md) | Access/refresh/activation token, xoay vòng, phát hiện dùng lại, khi nào mọi phiên bị thu hồi |
| [PAYROLL_LIFECYCLE.md](./PAYROLL_LIFECYCLE.md) | Trạng thái kỳ lương, điều kiện chuyển trạng thái, xử lý thay đổi hồi tố |
| [EFFECTIVE_DATING.md](./EFFECTIVE_DATING.md) | Dữ liệu theo khoảng hiệu lực, ràng buộc `EXCLUDE USING GIST`, múi giờ của "hôm nay" |

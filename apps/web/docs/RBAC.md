# Quản lý vai trò và quyền

Đăng nhập tại `/admin/login`, rồi chọn **Vai trò & quyền** hoặc mở trực tiếp `/admin/rbac`.
Trang dùng phiên Admin Console hiện tại, dành cho tài khoản có role `SYSTEM_ADMIN`.

UI đơn giản gồm hai mục:

- **Vai trò**: xem danh sách phân trang, tạo, sửa tên/mô tả/trạng thái và xóa vai trò tùy chỉnh.
  Nhấn **Phân quyền** tại một dòng để xem, gán hoặc gỡ permission của vai trò đó.
- **Quyền**: xem danh sách phân trang, lọc theo module, tạo, sửa mô tả/trạng thái và xóa
  permission chưa được sử dụng.

Form giữ nguyên mã role/permission và module khi sửa, theo contract của API. Nút xóa hoặc
vô hiệu hóa bị khóa đối với role hệ thống và permission `rbac.manage`. API vẫn kiểm tra
phân quyền, validation và các ràng buộc dữ liệu; lỗi API được hiển thị trên trang.

## Gọi API

Browser gọi `/api/admin-session/rbac/...`. Route Handler chỉ chuyển tiếp các endpoint
role/permission đã cho phép và lấy access token từ cookie admin `HttpOnly`.
Các method hỗ trợ là GET, POST, PUT và DELETE; query phân trang và module được chuyển tiếp.
Client dùng chung cơ chế refresh của phiên admin, thử lại một lần khi access token hết hạn.

Không đưa JWT vào JavaScript hoặc lưu token trong localStorage. API Spring Boot là nơi
quyết định quyền truy cập. Quy tắc đăng nhập của Admin Console vẫn theo [AUTH.md](AUTH.md);
danh sách role được phép gọi API RBAC được cấu hình riêng ở backend.

Thay đổi role/permission được phản ánh vào JWT mới sau khi đăng nhập hoặc refresh.
Danh sách endpoint, JSON mẫu và quy tắc xóa nằm trong
[tài liệu API RBAC](../../../api/docs/api/RBAC.md).

## Kiểm tra thủ công

1. Từ trang admin, mở **Quản lý vai trò & quyền**.
2. Tạo role tùy chỉnh, sửa tên/mô tả và kiểm tra danh sách đã cập nhật.
3. Chuyển sang **Quyền**, tạo permission với code như `employee.export_test`.
4. Trở lại **Vai trò**, nhấn **Phân quyền** và gán permission vừa tạo.
5. Thử xóa permission đang được gán: trang phải hiển thị lỗi `CONFLICT`.
6. Gỡ permission, xóa permission rồi xóa role tùy chỉnh.
7. Kiểm tra phân trang, lọc module và các nút bảo vệ role hệ thống.
8. Kiểm tra truy cập `/admin/rbac` khi chưa đăng nhập và gọi BFF không có cookie admin.

Không cần thêm dependency UI hoặc test runner để chạy trang này.

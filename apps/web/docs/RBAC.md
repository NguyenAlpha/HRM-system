# Quản lý vai trò và quyền

Giao diện có ở cả hai portal:

- HRM Workspace: đăng nhập tại `/login`, mở `/rbac`; dành cho account nghiệp vụ có `rbac.manage`, thường là `COMPANY_OWNER`.
- Admin Console: đăng nhập tại `/admin/login`, mở `/admin/rbac`; dành cho `SYSTEM_ADMIN` có `rbac.manage` theo cấu hình seed hiện tại.

UI đơn giản gồm hai mục:

- **Vai trò**: xem danh sách phân trang, tạo, sửa tên/mô tả/chính sách cấp và xóa vai trò tùy chỉnh.
  Nhấn **Phân quyền** để chọn nhiều permission, tìm kiếm, chọn/bỏ toàn bộ hoặc thao tác theo từng
  `PermissionModule`, sau đó lưu tất cả thay đổi bằng một request.
- **Quyền**: xem danh mục permission do ứng dụng định nghĩa và lọc theo module.

Form giữ nguyên mã role khi sửa. Role hệ thống và bộ permission của role hệ thống chỉ được xem.
API vẫn kiểm tra phân quyền, validation và các ràng buộc dữ liệu; lỗi API được hiển thị trên trang.

## Gọi API

Browser gọi `/api/session/rbac/...` ở HRM Workspace hoặc `/api/admin-session/rbac/...` ở Admin Console. Route Handler chỉ chuyển tiếp các endpoint role/permission đã cho phép và lấy access token từ cookie `HttpOnly` của portal tương ứng.
Các method hỗ trợ là GET, POST, PUT và DELETE; query phân trang và module được chuyển tiếp.
Client dùng chung cơ chế refresh của phiên admin, thử lại một lần khi access token hết hạn.

Không đưa JWT vào JavaScript hoặc lưu token trong localStorage. API Spring Boot là nơi
quyết định quyền truy cập. Quy tắc đăng nhập của Admin Console vẫn theo [AUTH.md](AUTH.md);
danh sách role được phép gọi API RBAC được cấu hình riêng ở backend.

Thay đổi role/permission được phản ánh vào JWT mới sau khi đăng nhập hoặc refresh.
Danh sách endpoint, JSON mẫu và quy tắc xóa nằm trong
[tài liệu API RBAC](../../../api/docs/api/RBAC.md).

## Kiểm tra thủ công

1. Từ portal phù hợp với account, mở **Vai trò & quyền**.
2. Tạo role tùy chỉnh, sửa tên/mô tả và kiểm tra danh sách đã cập nhật.
3. Nhấn **Phân quyền**, chọn cả module `EMPLOYEE`, bỏ một vài quyền rồi nhấn **Lưu quyền**.
4. Mở lại role và kiểm tra danh sách đã chọn được giữ nguyên.
5. Dùng **Chọn tất cả**, **Bỏ chọn tất cả**, tìm kiếm và **Hoàn tác** để kiểm tra trạng thái form.
6. Mở một system role và xác nhận danh sách permission chỉ đọc.
7. Kiểm tra phân trang, lọc module và các nút bảo vệ role hệ thống.
8. Kiểm tra truy cập `/rbac` và `/admin/rbac` khi chưa đăng nhập; gọi từng BFF không có cookie tương ứng.

Không cần thêm dependency UI hoặc test runner để chạy trang này.

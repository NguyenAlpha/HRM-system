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

Trang `/activate` nằm ngoài hai khu vực trên: không cần đăng nhập, dùng để tự đặt mật khẩu từ link mời
(xem [AUTH.md](./AUTH.md#kích-hoạt-tài-khoản)).

### HRM Workspace

Menu chia nhóm Nhân sự, Chấm công & nghỉ, Lương, Báo cáo, Hệ thống; mục và nhóm chỉ hiện khi tài khoản có quyền. Nút đăng xuất nằm ở chân menu.

| Route | Nội dung |
|:------|:---------|
| `/dashboard` | Tổng quan theo quyền: chấm công hôm nay, phép còn lại, đơn đang mở, lương gần nhất; việc chờ mình duyệt; kỳ lương gần nhất; quân số, nhân viên mới, người đang thử việc |
| `/profile` | Hồ sơ của tôi (chỉ đọc, qua `GET /api/employees/me`), vai trò đang có và đổi mật khẩu |
| `/employees`, `/employees/{employeeId}` | Hồ sơ, dữ liệu nhạy cảm, phân công, vòng đời nhân viên (nghỉ việc, xóa mềm hồ sơ tạo nhầm) và thẻ Lương & bảo hiểm (lương cơ bản, hồ sơ bảo hiểm/thuế, người phụ thuộc, phiếu lương) |
| `/accounts` | Danh sách tài khoản; gửi lại lời mời, đặt lại mật khẩu, vô hiệu hóa và kích hoạt lại |
| `/organization` | Đơn vị tổ chức, địa điểm, vị trí, ca làm và ngày lễ công ty theo năm (thêm bằng popup, xóa qua menu "…") |
| `/attendance` | Vào/ra ca, bảng công tháng, dialog điều chỉnh công và duyệt tăng ca; ngày lễ tô màu trên lịch |
| `/leave-requests` | Số dư phép năm, đơn nghỉ (chọn nhanh cả ngày/sáng/chiều), duyệt kèm ghi chú, điều chỉnh hạn mức và chốt phép năm |
| `/payroll` | Danh sách kỳ lương kèm số nhân viên, tổng thu nhập, tổng thực nhận; tạo kỳ mới |
| `/payroll/{periodId}` | Bảng lương của kỳ: các bước quy trình, tổng hợp, bảng mọi nhân viên có dòng tổng, tính/duyệt/trả/khóa/hủy, khoản cộng/trừ, xuất Excel, phiếu từng người |
| `/payslips` | Phiếu lương của chính người đăng nhập (từ kỳ đã duyệt) |
| `/allowances` | Chính sách phụ cấp chung: phụ cấp chức vụ theo chức danh (lịch sử, đổi mức) và các bậc phụ cấp thâm niên |
| `/reports/workforce` | Báo cáo nhân sự, thâm niên và lương cơ bản |
| `/rbac`, `/role-grant`, `/role-requests` | Quản lý quyền nghiệp vụ theo permission được cấp |

### Admin Console

| Route | Nội dung |
|:------|:---------|
| `/admin` | Tổng quan quản trị hệ thống |
| `/admin/employees`, `/admin/employees/{employeeId}` | Xem danh sách, hồ sơ nhân viên và cấp tài khoản (chỉ đọc hồ sơ) |
| `/admin/accounts` | Danh sách tài khoản; gửi lại lời mời, reset mật khẩu, vô hiệu hóa và kích hoạt lại (trừ tài khoản Company Owner) |
| `/admin/company-owner` | Khởi tạo Company Owner đầu tiên |
| `/admin/organization` | Dữ liệu tổ chức phục vụ bootstrap |
| `/admin/rbac` | CRUD custom role, xem permission và đồng bộ quyền |
| `/admin/role-grant` | Gán hoặc thu hồi role và permission override theo chính sách backend |

Danh sách nhân sự và tài khoản có ô tìm kiếm, bộ lọc nâng cao (trạng thái, đơn vị, vị trí, vai trò) và sắp xếp theo cột; tìm kiếm và lọc chạy ở API nên áp dụng trên toàn bộ dữ liệu, không chỉ trang đang xem.

Browser gọi API qua BFF và không nhận JWT. Việc hiển thị nút dựa trên permission chỉ phục vụ giao diện; backend tiếp tục kiểm tra permission và scope cho mọi thao tác.

Tài liệu Spring Boot API nằm tại [mục lục API](../../../api/docs/api/README.md). Quy trình chấm công và lương được mô tả riêng trong [ATTENDANCE.md](../../../api/docs/api/ATTENDANCE.md) và [PAYROLL.md](../../../api/docs/api/PAYROLL.md).

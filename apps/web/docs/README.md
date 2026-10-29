# HRM Web Documentation

Tài liệu dành cho ứng dụng Next.js trong `apps/web`.

## Danh mục

| Tài liệu | Nội dung |
|:---------|:---------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Cấu trúc thư mục và luồng request, giải thích bằng khái niệm backend |
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
| `/profile` | Hồ sơ của tôi (`GET /api/employees/me`), tự sửa liên hệ và học vấn qua popup (`PUT /api/employees/me/profile`), vai trò đang có và đổi mật khẩu |
| `/employees`, `/employees/{employeeId}` | Hồ sơ, dữ liệu nhạy cảm, phân công, vòng đời nhân viên (xác nhận chính thức, nghỉ việc, xóa mềm hồ sơ tạo nhầm) và thẻ Lương & bảo hiểm (lương cơ bản, hồ sơ bảo hiểm/thuế, người phụ thuộc, phiếu lương) |
| `/accounts` | Danh sách tài khoản; gửi lại lời mời, đặt lại mật khẩu, vô hiệu hóa, kích hoạt lại và xóa mềm (kèm lý do); bộ lọc "Tài khoản đã xóa" |
| `/organization` | Đơn vị tổ chức, địa điểm, vị trí, ca làm và ngày lễ công ty theo năm (thêm bằng popup, xóa qua menu "…") |
| `/attendance` | Bảng công tổng hợp (`attendance.read`): lưới nhân viên × ngày có ký hiệu X/M/P/V/L…, cột tổng công, muộn, nghỉ, vắng, tăng ca, cần xử lý; lọc theo tên/mã và đơn vị; bấm một dòng để xem lịch công, nhập giờ vào/ra, ghi vắng, của người đó. Bảng công của tôi cho mọi nhân viên. Tab Tăng ca chờ duyệt hiện khi có ngày mình là người duyệt (theo tuyến quản lý, không theo permission); `?tab=overtime` mở thẳng tab này; `?employeeId=` mở thẳng lịch công của người đó (từ menu "…" → Xem chấm công ở trang chi tiết nhân sự) |
| `/leave-requests` | Tab Đơn của tôi (phép năm còn lại, đơn của mình), Chờ tôi duyệt (đơn mình là người duyệt), Tất cả đơn (`request.read`: tìm, lọc trạng thái/loại/đơn vị/tháng, phân trang). `?tab=approvals` mở thẳng tab Chờ tôi duyệt. Tạo đơn bằng popup, chọn nhanh cả ngày/sáng/chiều hoặc một khoảng tùy ý (số công nghỉ do API tính theo ca), lưu nháp hoặc gửi duyệt ngay. Chỉ nhân viên tự tạo đơn của mình; người có `request.manage` được chọn cách trả lương khác mặc định |
| `/payroll` | Danh sách kỳ lương kèm số nhân viên, tổng thu nhập, tổng thực nhận; tạo kỳ mới |
| `/payroll/{periodId}` | Bảng lương của kỳ: các bước quy trình, tổng hợp, bảng mọi nhân viên có dòng tổng, tính/duyệt/trả/khóa/hủy, khoản cộng/trừ, xuất Excel, phiếu từng người |
| `/payslips` | Phiếu lương của chính người đăng nhập (từ kỳ đã duyệt), in từng phiếu; bảng lương năm (12 tháng × các khoản, dòng cả năm) có nút in. In chỉ ra phần được đánh dấu `print-area` (quy tắc `@media print` trong `app/globals.css`), đầu trang in có tên, mã số thuế và địa chỉ doanh nghiệp |
| `/allowances` | Chính sách phụ cấp chung: phụ cấp chức vụ theo chức danh (lịch sử, đổi mức) và các bậc phụ cấp thâm niên |
| `/reports/workforce` | Cơ cấu nhân sự: trình độ, thâm niên và phân bố lương cơ bản |
| `/resignations` | Đơn nghỉ việc: tab Đơn của tôi (nộp đơn bằng popup, ngày làm việc cuối và lý do; rút đơn chờ duyệt), Chờ tôi duyệt (duyệt có thể đổi ngày làm việc cuối, từ chối bắt buộc lý do), Tất cả đơn (`request.read`: tìm, lọc trạng thái/đơn vị, phân trang). `?tab=approvals` mở thẳng tab Chờ tôi duyệt |
| `/reports/turnover` | Biến động nhân sự theo năm (`report.hr.read`): quân số đầu/cuối, vào làm, nghỉ việc, tỷ lệ nghỉ việc theo tháng, biểu đồ quân số và danh sách vào/nghỉ |
| `/reports/monthly` | Tình hình nhân sự theo tháng (`report.hr.read`): quân số đầu/cuối, vào làm, nghỉ việc, và từng người với trạng thái, ngày nghỉ phép theo loại, vắng không phép; lọc và xuất CSV |
| `/reports/attendance` | Tổng hợp chấm công tháng (`attendance.read`): công tính lương, muộn/sớm, nghỉ, vắng, tăng ca, thiếu chấm công của từng người; sắp xếp theo vấn đề |
| `/reports/leave` | Phép năm (`report.hr.read`): được cấp, đã dùng, chờ duyệt, còn lại của từng người |
| `/reports/payroll` | Quỹ lương, bảo hiểm người lao động và thuế TNCN theo tháng và theo đơn vị (`report.payroll.read`, scope COMPANY) |

Mọi bảng báo cáo có nút **Xuất CSV** (UTF-8 có BOM, mở thẳng bằng Excel).
| `/company` | Thông tin doanh nghiệp (`GET /api/company`); menu chỉ hiện và nút sửa (popup, `PUT /api/company`) chỉ có với Company Owner (`company.manage`); thẻ "Chuyển giao Company Owner" chỉ hiện với người giữ role `COMPANY_OWNER` (`POST /api/company-owner/transfer`, xác nhận bằng mật khẩu trong popup, xong thì đăng xuất) |
| `/rbac`, `/role-grant`, `/role-requests` | Quản lý quyền nghiệp vụ theo permission được cấp; `/role-grant` có thêm thẻ "Bổ nhiệm Giám đốc mới" (`POST /api/accounts/{id}/role-assignments/director`, chọn ngày bàn giao) |

### Admin Console

| Route | Nội dung |
|:------|:---------|
| `/admin` | Tổng quan quản trị hệ thống |
| `/admin/employees`, `/admin/employees/{employeeId}` | Xem danh sách, hồ sơ nhân viên và cấp tài khoản (chỉ đọc hồ sơ) |
| `/admin/accounts` | Danh sách tài khoản; gửi lại lời mời, reset mật khẩu, vô hiệu hóa, kích hoạt lại và xóa mềm (trừ tài khoản Company Owner) |
| `/admin/company-owner` | Khởi tạo Company Owner đầu tiên, nhập luôn thông tin doanh nghiệp |
| `/admin/organization` | Dữ liệu tổ chức phục vụ bootstrap |
| `/admin/rbac` | CRUD custom role, xem permission và đồng bộ quyền |

Danh sách nhân sự và tài khoản có ô tìm kiếm, bộ lọc nâng cao (trạng thái, đơn vị, vị trí, vai trò) và sắp xếp theo cột; tìm kiếm và lọc chạy ở API nên áp dụng trên toàn bộ dữ liệu, không chỉ trang đang xem.

Lỗi API hiển thị cho người dùng qua `apiErrorMessage` (`lib/api-helpers.ts`): không hiện mã lỗi hay câu tiếng
Anh của API mà dùng câu tiếng Việt theo `error.code`; lỗi validation ghép tên trường với câu tiếng Việt của API.
Giới hạn độ dài trên form (`maxLength`, `.max()` của zod) khớp với `@Size` của request DTO.

Browser gọi API qua BFF và không nhận JWT. Việc hiển thị nút dựa trên permission chỉ phục vụ giao diện; backend tiếp tục kiểm tra permission và scope cho mọi thao tác.

Tài liệu Spring Boot API nằm tại [mục lục API](../../../api/docs/api/README.md). Quy trình chấm công và lương được mô tả riêng trong [ATTENDANCE.md](../../../api/docs/api/ATTENDANCE.md) và [PAYROLL.md](../../../api/docs/api/PAYROLL.md).

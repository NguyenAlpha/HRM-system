# Tính năng hiện có

Bản kiểm kê những gì hệ thống **đang chạy được**, dùng để biết nên xây tiếp cái gì. Nội dung
được đối chiếu từ controller, service và route frontend, không phải từ kế hoạch.

Quy ước trạng thái:

| Ký hiệu | Nghĩa |
|---|---|
| Đủ | Có API, có màn hình, dùng được từ đầu đến cuối |
| Chỉ API | API chạy được nhưng chưa có màn hình |

## 1. Tổng quan

| Module | Trạng thái | Màn hình |
|---|---|---|
| Xác thực và phiên | Đủ | `/login`, `/admin/login`, `/activate` |
| Tài khoản | Đủ | `/accounts`, `/admin/accounts`, trong trang nhân viên |
| Phân quyền RBAC | Đủ | `/rbac`, `/admin/rbac`, `/role-grant`, `/role-requests` |
| Hồ sơ nhân viên | Đủ | `/employees`, `/employees/{id}`, `/admin/employees` (chỉ xem) |
| Cơ cấu tổ chức | Đủ | `/organization`, `/admin/organization` |
| Ca làm việc và ngày lễ | Đủ | `/organization` |
| Nghỉ phép | Đủ | `/leave-requests` |
| Thông báo đơn nghỉ | API có sẵn | Nút thông báo đang ẩn trong HRM Workspace |
| Đơn nghỉ việc | Đủ | `/resignations` |
| Chấm công | Đủ | `/attendance` |
| Lương, bảo hiểm, thuế | Đủ | `/payroll`, `/payslips`, `/allowances`, thẻ lương trong `/employees/{id}` |
| Báo cáo | Đủ | `/reports/workforce`, `/reports/turnover`, `/reports/monthly`, `/reports/attendance`, `/reports/leave`, `/reports/payroll` |

Toàn hệ thống có 29 controller, 48 permission trên 8 module, 8 system role và 33 bảng database.

---

## 2. Xác thực và phiên

Hai portal dùng chung một API nhưng tách cookie: HRM Workspace cho người dùng nghiệp vụ, Admin
Console cho `SYSTEM_ADMIN`. Trình duyệt không bao giờ giữ JWT; Next.js giữ token trong cookie
`HttpOnly` và gắn header `Authorization` ở phía server.

| Chức năng | Endpoint |
|---|---|
| Đăng nhập theo portal | `POST /api/auth/login` |
| Xoay vòng refresh token | `POST /api/auth/refresh` |
| Lấy thông tin phiên | `GET /api/auth/me` |
| Đăng xuất, thu hồi refresh token | `POST /api/auth/logout` |
| Đổi mật khẩu | `POST /api/auth/change-password` |
| Kích hoạt account bằng token mời | `POST /api/account-activations/{token}/complete` |

Trạng thái account: `PENDING` → `ACTIVE`, và `LOCKED` / `DISABLED`.

Mọi API đều có giới hạn số request theo IP và theo người dùng; sai mật khẩu quá 5 lần trong 15 phút thì phải chờ
mới đăng nhập lại được (không khoá tài khoản). Admin Console có trang thống kê **Giới hạn truy cập**
(`GET /api/system/rate-limit/stats`) và trang **Lưu lượng API** (`GET /api/system/traffic/stats`): số request, thời
gian xử lý trung bình và số lỗi của từng API kể từ lần khởi động. Cả hai trang có biểu đồ theo thời gian (60 phút, 1 ngày,
7 ngày): request và số bị chặn ở trang Giới hạn truy cập; request, lỗi và thời gian xử lý ở trang Lưu lượng API.
Trang Giới hạn truy cập tự làm mới mỗi 15 giây. Trên trang này, quản trị viên hệ thống
còn sửa hạn mức, tắt/bật từng mức (trừ mức chống dò mật khẩu) và khôi phục mặc định; thay đổi lưu vào database và có
hiệu lực ngay.

Chi tiết: [api/docs/api/AUTH.md](../api/docs/api/AUTH.md), [apps/web/docs/AUTH.md](../apps/web/docs/AUTH.md),
[RATE_LIMIT.md](../api/docs/concepts/RATE_LIMIT.md), [API_TRAFFIC.md](../api/docs/concepts/API_TRAFFIC.md).

---

## 3. Tài khoản

HR tạo account cho hồ sơ nhân viên hợp lệ, hệ thống phát token kích hoạt để người dùng tự đặt
mật khẩu. Không ai đặt mật khẩu hộ người khác.

| Chức năng | Endpoint | Permission |
|---|---|---|
| Cấp account cho nhân viên | `POST /api/accounts` | `account.provision` |
| Xem danh sách, xem chi tiết | `GET /api/accounts`, `GET /api/accounts/{id}` | `account.read` |
| Gửi lại thư mời | `POST /api/accounts/{id}/invitations/resend` | `account.activation.manage` |
| Đặt lại mật khẩu | `POST /api/accounts/{id}/password-reset` | `account.activation.manage` |
| Khóa, mở khóa | `POST /api/accounts/{id}/suspend`, `/activate` | `account.manage` |
| Xóa mềm tài khoản (kèm lý do, cả tài khoản chưa kích hoạt) | `POST /api/accounts/{id}/delete` | `account.manage` |

---

## 4. Phân quyền RBAC

Danh mục permission thuộc sở hữu của code và chỉ đọc qua API. Role thì tạo được: `COMPANY_OWNER`
tạo custom role và chọn các permission `DELEGABLE`. Quyền thực tế = permission của role đang gán
× phạm vi dữ liệu (`SELF`, `ORG_UNIT`, `LOCATION`, `COMPANY`), rồi áp thêm override nếu có.

| Chức năng | Endpoint | Permission |
|---|---|---|
| CRUD custom role | `/api/roles` | `rbac.manage` |
| Gán permission cho role | `/api/roles/{id}/permissions` | `rbac.manage` |
| Xem danh mục permission | `/api/permissions` | `rbac.manage` |
| Gán, thu hồi role cho account | `/api/accounts/{id}/role-assignments` | `account.role.assign` |
| Ngoại lệ quyền trên từng lần gán | `.../permission-overrides` | `account.permission.override.manage` |
| Đề xuất và duyệt cấp role | `/api/role-assignment-requests` | `role.assignment.request` / `.approve` |

Mỗi role có `grant_policy` quyết định đường cấp: `AUTO`, `HR_ASSIGNABLE`, `OWNER_APPROVAL`,
`SYSTEM_ONLY`. Override có vòng đời `SCHEDULED` → `ACTIVE` → `EXPIRED` / `REVOKED`.

Thay đổi phân quyền chỉ vào JWT mới sau khi đăng nhập hoặc refresh.

Ma trận đầy đủ role → permission: [api/docs/api/RBAC.md](../api/docs/api/RBAC.md).

---

## 5. Hồ sơ nhân viên

| Chức năng | Endpoint | Permission |
|---|---|---|
| Tạo hồ sơ, mã nhân viên tự sinh (`NV0001`, `NV0002`…) | `POST /api/employees` | `employee.create` |
| Danh sách theo phạm vi được phân công | `GET /api/employees` | `employee.list.read` |
| Xem chi tiết | `GET /api/employees/{id}` | `employee.read` |
| Cập nhật hồ sơ | `PUT /api/employees/{id}` | `employee.update` |
| Nhân viên tự sửa liên lạc và học vấn của mình | `PUT /api/employees/me/profile` | `profile.self.update` |
| Xác nhận hết thử việc | `POST /api/employees/{id}/confirm` | `employee.probation.confirm` |
| Kết thúc làm việc: nghỉ việc, chấm dứt hợp đồng, nghỉ hưu; lên lịch nếu ngày làm việc cuối sau hôm nay, hủy lịch | `POST`, `DELETE /api/employees/{id}/employment-end` | `employee.lifecycle.manage` |
| Nộp đơn nghỉ việc, rút đơn chờ duyệt | `POST /api/resignation-requests`, `/{id}/cancel` | `request.self.create`, `request.self.cancel` |
| Duyệt (có thể đổi ngày làm việc cuối), từ chối đơn nghỉ việc | `POST /api/resignation-requests/{id}/approve`, `/reject` | Người duyệt theo tuyến quản lý |
| Xóa mềm hồ sơ tạo nhầm | `POST /api/employees/{id}/soft-delete` | `employee.delete` |
| Xem phân công và lịch sử | `GET /api/employees/{id}/assignments` | `employee.assignment.read` |
| Điều chuyển, bổ nhiệm | `POST /api/employees/{id}/assignments` | `employee.assignment.manage` |
| Đọc dữ liệu nhạy cảm | `GET /api/employees/{id}/sensitive` | `employee.sensitive.read` |
| Cập nhật dữ liệu nhạy cảm | `PUT /api/employees/{id}/sensitive` | `employee.sensitive.manage` |

Trạng thái lao động: `PROBATION`, `ACTIVE`, `RESIGNED`, `TERMINATED`, `RETIRED`. Loại hợp đồng:
`FULL_TIME`, `PART_TIME`, `TEMPORARY`. `PROBATION` → `ACTIVE` qua `confirm`; từ `PROBATION` hoặc
`ACTIVE` kết thúc sang một trong ba trạng thái `RESIGNED`, `TERMINATED`, `RETIRED` qua `employment-end`.
Đơn nghỉ việc đã duyệt kết thúc hợp đồng (`RESIGNED`) tự động vào ngày sau ngày làm việc cuối, bằng
job chạy lúc khởi động và 00:10 mỗi đêm; trước đó nhân viên vẫn đăng nhập và chấm công bình thường.
HR kết thúc làm việc với ngày làm việc cuối sau hôm nay cũng chỉ lên lịch, và job cùng giờ kết thúc
khi ngày đó đã qua.

Phân công giữ cả đơn vị, địa điểm, vị trí, ca làm và người quản lý trực tiếp, có thời gian hiệu
lực để tính lại đúng dữ liệu lịch sử.

`GET`/`PUT /{id}/sensitive` tách riêng khỏi hồ sơ thường để quyền đọc hồ sơ không kéo theo quyền
đọc CCCD và tài khoản ngân hàng. `PUT` là thay thế toàn bộ nhóm trường, không vá từng phần.

Kết thúc làm việc là thao tác gộp, giống nhau cho cả ba trạng thái: đóng phân công đang mở tại ngày
kết thúc, chuyển account sang `DISABLED` và thu hồi toàn bộ refresh token của account đó. Đây là
bước thực thi, không phải bước phê duyệt.

Xóa mềm chỉ dành cho hồ sơ tạo nhầm: đã có account, lương, đơn nghỉ, chấm công hoặc phiếu lương
thì API từ chối và yêu cầu dùng luồng kết thúc làm việc. Phân công được tính theo số lượng — bản ghi đầu
tiên do chính luồng tạo hồ sơ sinh ra nên không tính, từ bản ghi thứ hai mới coi là đã điều chuyển.

Ba endpoint `sensitive`, `employment-end` và `soft-delete` được dùng trên trang
`/employees/{employeeId}`: thẻ dữ liệu nhạy cảm (đọc/sửa theo `employee.sensitive.*`), nút kết
thúc làm việc và nút xóa hồ sơ, mỗi phần chỉ hiện khi account có permission tương ứng.

---

## 6. Cơ cấu tổ chức

Bốn danh mục, tất cả đều CRUD đầy đủ với `organization.read` để xem và `organization.manage` để
sửa, xóa mềm bằng `deleted_at`.

| Danh mục | Endpoint | Ghi chú |
|---|---|---|
| Đơn vị tổ chức | `/api/organization-units` | Cây `BOARD` / `DEPARTMENT` / `TEAM`, có `/tree` |
| Địa điểm làm việc | `/api/work-locations` | Cây `HEAD_OFFICE` / `BRANCH` / `WAREHOUSE`, có `/tree` |
| Chức danh | `/api/job-positions` | Có cờ `is_managerial`, không tự cấp quyền |
| Ca làm việc | `/api/work-shifts` | Giờ vào/ra, phút và giờ bắt đầu nghỉ, công chuẩn, ca qua đêm |

Cả bốn danh mục có tab riêng trên `/organization` (và `/admin/organization`) để xem, tạo, sửa, xóa.

Đơn vị và địa điểm là hai chiều độc lập: một nhân viên thuộc phòng Nhân sự vẫn có thể làm ở chi
nhánh. Chức danh quản lý không tự sinh quyền quản lý trong hệ thống.

`SYSTEM_ADMIN` có riêng `POST /api/system/organization/company-owner` để tạo Company Owner đầu
tiên, dùng một lần khi khởi tạo hệ thống; bước này nhập luôn thông tin doanh nghiệp (`company_profile`).
Sau khi bàn giao, chỉ Company Owner sửa thông tin doanh nghiệp qua `PUT /api/company`
(`company.manage`); mọi người đăng nhập xem được qua `GET /api/company`.

---

## 7. Ngày lễ và chấm công

Lịch làm việc chuẩn là thứ 2 đến thứ 7, trừ ngày trong `company_holidays`. Ngày lễ được hưởng nguyên
lương vì không tính vào số ngày công chuẩn của tháng; đi làm ngày lễ được duyệt tăng ca hệ số tối thiểu 3.
Lịch lễ, Tết 2025 và 2026 (11 ngày mỗi năm) được seed sẵn; từ 2027 HR tự khai báo.

Giờ vào/ra được ghi qua `POST /api/attendance/punches` với thời điểm cụ thể; sau này máy chấm công
của công ty sẽ gửi vào đây (tích hợp máy chưa triển khai). Nhân viên chỉ xem lịch sử chấm công trên
web, không tự chấm. HR đối soát bằng hai thao tác: nhập giờ vào/ra thực tế (ghi đè được, tạo được
ngày ngoài lịch như Chủ nhật) hoặc ghi vắng không phép.

| Chức năng | Endpoint | Permission |
|---|---|---|
| Khai báo, xóa ngày lễ | `/api/company-holidays` | `organization.manage` |
| Xem ngày lễ (mọi nhân viên) | `GET /api/company-holidays` | `holiday.read` |
| Chuẩn bị bảng công tháng | `POST /api/attendance/prepare` | `attendance.manage` |
| Nhập hoặc sửa giờ vào/ra | `POST /api/attendance/punches` | `attendance.manage` |
| Ghi vắng không phép, sửa số phút | `PUT /api/attendance/{id}` | `attendance.manage` |
| Duyệt tăng ca | `POST /api/attendance/{id}/overtime-approval` | Người duyệt của nhân viên (quản lý trực tiếp → Giám đốc → Chủ sở hữu) |
| Xem bản ghi công | `GET /api/attendance/{id}`, `/employees/{id}` | `attendance.self.read` / `attendance.read` |

Trạng thái ngày công: `PRESENT`, `PAID_LEAVE`, `UNPAID_LEAVE`, `MATERNITY_LEAVE`, `SICK_LEAVE`,
`UNAUTHORIZED_ABSENCE`, `HOLIDAY`, `MISSING_PUNCH`.

`prepare` tạo bản ghi `MISSING_PUNCH` cho mọi ngày làm việc chưa có dữ liệu, gọi lặp không tạo
trùng. Backend tự chạy việc này cho tháng hiện tại lúc khởi động và 00:05 mỗi đêm, bỏ qua
(và ghi log) nhân viên thiếu phân công/ca; tắt bằng `attendance.auto-prepare.enabled=false`. Kỳ lương không chuyển sang `CALCULATED` khi còn `MISSING_PUNCH`.

Duyệt tăng ca ghi cả số phút, hệ số và cờ miễn thuế. Hệ số tối thiểu 1,5 ngày thường, 2 Chủ nhật,
3 ngày lễ. Giờ chấm vào Chủ nhật hoặc ngày lễ không thành công thường, chỉ được trả khi duyệt
tăng ca.

Chi tiết: [api/docs/api/ATTENDANCE.md](../api/docs/api/ATTENDANCE.md).

---

## 8. Nghỉ phép

| Chức năng | Endpoint |
|---|---|
| Tạo đơn nháp | `POST /api/leave-requests` |
| Gửi duyệt, hủy | `POST /api/leave-requests/{id}/submit`, `/cancel` |
| Duyệt, từ chối | `POST /api/leave-requests/{id}/approve`, `/reject` |
| Xem đơn của mình, của nhân viên, đơn chờ duyệt | `GET /{id}`, `/employees/{id}`, `/pending` |
| Xem số dư phép năm | `GET /api/leave-entitlements/employees/{id}` |

Vòng đời: `DRAFT` → `PENDING` → `APPROVED` / `REJECTED`, hoặc `CANCELLED`.
Chỉ nhân viên gắn với account đang đăng nhập được tạo, gửi hoặc hủy đơn của mình; form không cho nhập mã nhân viên.

Khi gửi đơn, người duyệt được chốt và HR có quyền xem nhân viên nhận thông báo trong ứng dụng.
Khi đơn được duyệt hoặc từ chối, nhân viên nộp đơn nhận thông báo kết quả.
Nút thông báo đang ẩn trên giao diện; API vẫn lưu và trả thông báo cho đúng account.
Chi tiết API: [NOTIFICATIONS.md](../api/docs/api/NOTIFICATIONS.md).

Nghỉ `ANNUAL` có hạn mức năm: 12 ngày, chia tỷ lệ theo số tháng làm việc nếu vào làm giữa năm (tháng
vào làm tính trọn tháng). Không cộng theo thâm niên, không chuyển phép dư sang năm sau, HR không điều
chỉnh tay; hạn mức được tính mỗi lần, không lưu database. Duyệt đơn vượt số dư bị chặn bằng `409`. Khi kiểm tra, các
đơn `PENDING` khác được tính như đã tiêu nên nhiều đơn cùng chờ không thể cùng lọt qua. Số dư lưu
bằng phút để khớp với chấm công và payroll; các loại nghỉ còn lại không trừ hạn mức.

Loại nghỉ `ANNUAL`, `SICK`, `MATERNITY`, `UNPAID`, `OTHER`. Cách trả lương do server suy ra từ loại nghỉ, chỉ `request.manage` ghi đè được: `EMPLOYER_PAID`,
`SOCIAL_INSURANCE`, `UNPAID`. Duyệt đơn tự gắn số phút nghỉ vào bảng công: nghỉ có lương cộng
vào phút được trả lương, nghỉ không lương và nghỉ do bảo hiểm chi trả thì không.

---

## 9. Lương, bảo hiểm và thuế

Phạm vi hiện tại: nhân viên **cư trú tại Việt Nam**, trả bằng VND, kỳ lương tháng **từ 01/2020
đến 12/2026**. Quy tắc thuế và bảo hiểm có ngày hiệu lực trong database, nạp từ 01/01/2020 đến 31/12/2026;
kỳ ngoài phạm vi sẽ dừng với lỗi yêu cầu bổ sung quy tắc.

**Dữ liệu đầu vào do HR nhập**

| Chức năng | Endpoint | Permission |
|---|---|---|
| Lịch sử lương cơ bản | `/api/compensation/employees/{id}/salary-history` | `compensation.manage` / `.read` |
| Hồ sơ bảo hiểm và thuế | `.../payroll-profiles` | `compensation.manage` / `.read` |
| Người phụ thuộc giảm trừ | `.../tax-dependents` | `compensation.manage` / `.read` |
| Phụ cấp chức vụ theo chức danh | `/api/compensation/position-allowances`, `/api/compensation/job-positions/{id}/allowances` | `compensation.manage` / `.read` |
| Phụ cấp thâm niên theo bậc năm | `/api/compensation/seniority-allowances` | `compensation.manage` / `.read` |

**Kỳ lương**

| Chức năng | Endpoint | Permission |
|---|---|---|
| Tạo kỳ, xem kỳ | `/api/payroll/periods` | `payroll.calculate` |
| Tính lương | `POST /api/payroll/periods/{id}/calculate` | `payroll.calculate` |
| Duyệt | `POST .../approve` | `payroll.approve` |
| Xác nhận đã trả | `POST .../mark-paid` | `payroll.mark_paid` |
| Khóa kỳ | `POST .../lock` | `payroll.lock` |
| Xuất bảng lương của kỳ ra Excel | `GET /api/payroll/periods/{id}/export` | `report.payroll.read` |
| Xem bảng lương của kỳ | `GET /api/payroll/periods/{id}/payslips` | `report.payroll.read` |
| Thưởng, phụ cấp khác, khấu trừ nhập tay theo kỳ | `/api/payroll/periods/{id}/adjustments` | `payroll.calculate` |
| Xem phiếu lương | `/api/payroll/payslips/{id}`, `/employees/{id}/payslips` | `payroll.self.read` |
| In phiếu lương tháng và bảng lương năm của mình (in qua trình duyệt, lưu PDF được) | Trang `/payslips`, cùng API trên | `payroll.self.read` |

Vòng đời kỳ: `DRAFT` → `CALCULATED` → `APPROVED` → `PAID` → `LOCKED`, có thể `CANCELLED` trước
khi duyệt (phiếu lương đã tính của kỳ bị hủy cũng bị xóa). Từ `APPROVED` trở đi mọi đường sửa dữ liệu công đều bị chặn và phiếu lương là snapshot
bất biến.

Phiếu lương tính: lương cơ bản theo phút được trả lương, phụ cấp chức vụ, phụ cấp thâm niên, tăng
ca, rồi trừ BHXH/BHYT/BHTN phần nhân viên và thuế TNCN lũy tiến. Phiếu lưu lại căn cứ đóng sau
trần, số tăng ca miễn thuế, thu nhập tính thuế và cả `id` của bộ quy tắc đã dùng.

Hai vai trò tách nhau có chủ đích: `PAYROLL_ACCOUNTANT` tính nhưng không duyệt, `PAYROLL_APPROVER`
duyệt nhưng không tính.

Công thức, các mức 2020–2026 và nguồn pháp lý: [api/docs/api/PAYROLL.md](../api/docs/api/PAYROLL.md).

---

## 10. Báo cáo

Hai báo cáo tổng hợp, đều áp phạm vi của role assignment đang hiệu lực và xét dữ liệu tại
`asOfDate`. Lọc chung theo đơn vị, địa điểm và trạng thái lao động.

| Báo cáo | Endpoint | Permission |
|---|---|---|
| Phân bố trình độ và thâm niên | `GET /api/reports/hr/workforce-distribution` | `report.hr.read` |
| Phân bố lương cơ bản | `GET /api/reports/payroll/salary-distribution` | `report.payroll.read` |
| Biến động nhân sự theo năm | `GET /api/reports/hr/turnover` | `report.hr.read` |
| Tình hình nhân sự theo tháng (từng người: vào, nghỉ việc, nghỉ phép, vắng) | `GET /api/reports/hr/monthly-status` | `report.hr.read` |
| Phép năm của từng nhân viên | `GET /api/reports/hr/leave-balances` | `report.hr.read` |
| Quỹ lương, bảo hiểm, thuế theo tháng và đơn vị | `GET /api/reports/payroll/summary` | `report.payroll.read` (scope COMPANY) |
| Tổng hợp chấm công tháng | `GET /api/attendance/timesheet` | `attendance.read` |

Báo cáo lương chỉ trả số liệu tổng hợp theo khoảng, không trả lương của từng cá nhân.

---

## 11. Chưa có

- Lịch làm việc cố định thứ 2–thứ 7; chưa có lịch cá nhân hoặc xoay ca theo ngày.
- Mỗi nhân viên mỗi ngày chỉ một cặp vào/ra và một đơn nghỉ. Nghỉ nửa ngày rồi đi làm nửa còn lại được
  tính đủ công tự động, nhưng chưa có hai đơn khác loại trong cùng một ngày.
- Chưa có phụ trội ca đêm, hoa hồng tính tự động, truy thu, hoàn thuế hay quyết toán năm; thưởng và
  khoản khác chỉ nhập tay theo kỳ.
- Chưa hỗ trợ người không cư trú thuế.
- Chưa tính phần bảo hiểm doanh nghiệp đóng và tổng chi phí nhân sự; thiết kế để làm sau ở
  [Mở rộng: bảo hiểm doanh nghiệp đóng](extensions/EMPLOYER_INSURANCE.md).
- Ngoại lệ quyền chỉ Company Owner tạo được; HR chưa đề nghị được để Owner duyệt. Thiết kế để làm sau ở
  [Mở rộng: HR đề nghị ngoại lệ quyền](extensions/PERMISSION_OVERRIDE_REQUESTS.md).
- Quy tắc thuế và bảo hiểm chỉ nạp từ 2020 đến hết 2026, phải thêm bằng migration khi chính sách đổi.
  Seed lương bỏ qua (kèm cảnh báo trong log) các tháng chưa có quy tắc, nên vẫn chạy được sau 2026.
- Phép năm không cộng theo thâm niên (Điều 114) và không chuyển phép dư sang năm sau.
- Chưa tích hợp máy chấm công vật lý.
- Chưa có nhật ký audit cho toàn bộ thay đổi dữ liệu.
- Chưa mã hóa cấp cột cho dữ liệu nhạy cảm; hiện chỉ kiểm soát bằng quyền và phạm vi.
- Một doanh nghiệp duy nhất, chưa multi-tenant.

---

## 12. Tài liệu liên quan

- [Tổng quan kiến trúc](OVERVIEW.md)
- [Cơ cấu tổ chức và phân quyền](ORGANIZATION_STRUCTURE.md)
- [Mục lục API](../api/docs/api/README.md)
- [Schema database](../api/docs/database/DATABASE_SCHEMA.md)
- [Tài liệu web](../apps/web/docs/README.md)
